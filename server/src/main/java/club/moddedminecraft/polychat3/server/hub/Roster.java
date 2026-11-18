package club.moddedminecraft.polychat3.server.hub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import club.moddedminecraft.polychat3.core.payloads.PlayerInfo;
import club.moddedminecraft.polychat3.core.payloads.ServerState;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SEvent;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SHandshake;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerList;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SServerStatus;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerEntry;

/**
 * What the hub knows about each game server: its handshake details, state and players. Feeds the relayed
 * {@code serverStatus} and {@code handshakeAccepted.servers}. Only used on the hub thread.
 */
final class Roster {
    /** No frame for this long marks a server {@code unresponsive}, as in PROTOCOL.md "Keepalive". */
    static final long QUIET_SECONDS = 45;
    /** {@link #QUIET_SECONDS} in {@link System#nanoTime()} units. */
    private static final long QUIET_NANOS = TimeUnit.SECONDS.toNanos(QUIET_SECONDS);

    /** By game server id, sorted so {@link #others} lists servers in the same order every time. */
    private final Map<String, Entry> servers = new TreeMap<>();

    /**
     * Records a server after its handshake is accepted, replacing what was known before. Players start empty:
     * the client sends a fresh {@code playerList} right after {@code handshakeAccepted}.
     *
     * @param now {@link System#nanoTime()} when the handshake was accepted, which counts as hearing from the server.
     */
    void connected(C2SHandshake handshake, long now) {
        servers.put(handshake.getServer(), new Entry(handshake, now));
    }

    /**
     * Applies a new, not duplicate, event: {@code playerList} replaces the players, {@code playerJoin} and
     * {@code playerLeave} change them, {@code serverStatus} sets the state. Other events change nothing.
     *
     * @throws IllegalStateException if {@link #connected} never saw this id.
     */
    void update(String id, C2SEvent event) {
        Entry entry = find(id);
        if (event instanceof C2SPlayerList) {
            C2SPlayerList list = (C2SPlayerList) event;
            entry.players = new ArrayList<>(list.getPlayers());
        } else if (event instanceof C2SPlayerJoin) {
            C2SPlayerJoin join = (C2SPlayerJoin) event;
            entry.players.add(new PlayerInfo(join.getPlayer(), join.getUuid()));
        } else if (event instanceof C2SPlayerLeave) {
            C2SPlayerLeave leave = (C2SPlayerLeave) event;
            entry.players.removeIf(p -> p.getUuid().equals(leave.getUuid()));
        } else if (event instanceof C2SServerStatus) {
            C2SServerStatus status = (C2SServerStatus) event;
            entry.state = status.getState();
            entry.lastState = status.getState();
        }
    }

    /**
     * The hub got a valid frame from this server, heartbeat or event. Records the time, and if the server was
     * {@code unresponsive}, puts back the last state it reported, as in PROTOCOL.md "Keepalive".
     *
     * @param now {@link System#nanoTime()} when the frame arrived.
     * @return true if the state changed, so the hub relays a {@code serverStatus}.
     * @throws IllegalStateException if {@link #connected} never saw this id.
     */
    boolean heard(String id, long now) {
        Entry entry = find(id);
        entry.lastHeard = now;
        if (entry.state != ServerState.UNRESPONSIVE) {
            return false; // The other servers already have this state
        }
        entry.state = entry.lastState;
        return true;
    }

    /**
     * Marks every {@code started} or {@code stopping} server the hub hasn't heard from for 45 s as
     * {@code unresponsive}, as in PROTOCOL.md "Keepalive". Skips {@code starting}, because a game server sends no
     * heartbeat while it loads, and every other state, because those servers have no live connection, are closing
     * or are already marked.
     *
     * @param now {@link System#nanoTime()} when the check runs.
     * @return the ids just marked, sorted, so the hub relays one {@code serverStatus} for each.
     */
    List<String> unresponsive(long now) {
        List<String> marked = new ArrayList<>();
        for (Entry entry : servers.values()) {
            if (entry.state != ServerState.STARTED && entry.state != ServerState.STOPPING) {
                continue;
            }
            if (now - entry.lastHeard <= QUIET_NANOS) {
                continue; // Heard from recently
            }
            entry.state = ServerState.UNRESPONSIVE;
            marked.add(entry.id);
        }
        return marked;
    }

    /**
     * The server's live connection closed. Marks it {@code offline} with no players, unless it last reported
     * {@code stopped} or {@code crashed}, as in PROTOCOL.md "Shutting down".
     *
     * @return true if the state changed, so the hub relays a {@code serverStatus}.
     * @throws IllegalStateException if {@link #connected} never saw this id.
     */
    boolean disconnected(String id) {
        Entry entry = find(id);
        if (entry.state == ServerState.STOPPED || entry.state == ServerState.CRASHED) {
            return false; // The other servers already have this state
        }
        entry.state = ServerState.OFFLINE;
        entry.players.clear();
        return true;
    }

    /**
     * @return what the hub knows about this server now, with its own copy of the players.
     * @throws IllegalStateException if {@link #connected} never saw this id.
     */
    S2CServerEntry entry(String id) {
        Entry entry = find(id);
        return new S2CServerEntry(entry.id, entry.name, entry.colour, entry.address, entry.maxPlayers,
                entry.state, new ArrayList<>(entry.players));
    }

    /**
     * @return every server the hub knows except {@code id}, in any state and sorted by id, each with its own copy
     *         of the players. For {@code handshakeAccepted.servers}.
     */
    List<S2CServerEntry> others(String id) {
        List<S2CServerEntry> others = new ArrayList<>();
        for (Entry entry : servers.values()) {
            if (!entry.id.equals(id)) {
                others.add(entry(entry.id));
            }
        }
        return others;
    }

    /** @throws IllegalStateException if {@link #connected} never saw this id, which is a hub bug. */
    private Entry find(String id) {
        Entry entry = servers.get(id);
        if (entry == null) {
            throw new IllegalStateException("unknown server " + id);
        }
        return entry;
    }

    /** One game server's record. Copied from its handshake, then changed by its events. */
    private static final class Entry {
        final String id;
        final String name;
        final String colour;
        final String address;
        final int maxPlayers;
        /** What the other servers see: the client's own state, or one the hub set, like {@code offline}. */
        ServerState state;
        /** The last state the client itself reported, restored when an {@code unresponsive} server is heard again. */
        ServerState lastState;
        /** {@link System#nanoTime()} of the last frame from this server, or of its handshake. */
        long lastHeard;
        List<PlayerInfo> players = new ArrayList<>();

        Entry(C2SHandshake handshake, long now) {
            this.id = handshake.getServer();
            this.name = handshake.getName();
            this.colour = handshake.getColour();
            this.address = handshake.getAddress();
            this.maxPlayers = handshake.getMaxPlayers();
            this.lastState = handshake.getState();
            this.state = handshake.getState();
            this.lastHeard = now;
        }
    }
}