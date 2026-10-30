package club.moddedminecraft.polychat3.server.hub;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import club.moddedminecraft.polychat3.core.payloads.c2s.C2SChatMessage;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SEvent;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerList;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SServerStatus;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CChatMessage;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CRelayed;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerEntry;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerStatus;

/**
 * Turns a client's event, or a status the hub sets itself, into the frame the other game servers get, numbered
 * with the hub's relay {@code seq}, as in PROTOCOL.md "Replay to the client". Only used on the hub thread.
 */
final class Relay {
    /** How many relayed frames the ring keeps for replay. */
    private static final int KEPT = 200;
    /** The relay {@code seq} of the last frame built, 0 before the first. One number for the whole hub. */
    private long lastRelayed;
    /**
     * The last {@link #KEPT} relayed {@code chatMessage}, {@code playerJoin} and {@code playerLeave} frames, oldest
     * first.
     */
    private final Deque<S2CRelayed> kept = new ArrayDeque<>();

    /**
     * @param origin the roster entry of the server the event came from, already updated with this event. Gives
     * the relayed frame its {@code server} and {@code serverName}, and {@code serverStatus} everything else.
     * @return the frame to relay, with the next relay {@code seq}, or null if this event type isn't relayed. The
     * {@code seq} only advances when a frame is returned.
     */
    S2CRelayed relayed(S2CServerEntry origin, C2SEvent event) {
        String server = origin.getId();
        String serverName = origin.getName();

        if (event instanceof C2SChatMessage) {
            C2SChatMessage chat = (C2SChatMessage) event;
            return keep(new S2CChatMessage(server, serverName, ++lastRelayed, chat.getPlayer(),
                    chat.getSenderId(), chat.getMessage()));
        }

        if (event instanceof C2SPlayerJoin) {
            C2SPlayerJoin join = (C2SPlayerJoin) event;
            return keep(new S2CPlayerJoin(server, serverName, ++lastRelayed, join.getPlayer(), join.getUuid(),
                    join.getOnline()));
        }

        if (event instanceof C2SPlayerLeave) {
            C2SPlayerLeave leave = (C2SPlayerLeave) event;
            return keep(new S2CPlayerLeave(server, serverName, ++lastRelayed, leave.getPlayer(), leave.getUuid(),
                    leave.getOnline()));
        }

        if (event instanceof C2SPlayerList) {
            C2SPlayerList list = (C2SPlayerList) event;
            return new S2CPlayerList(server, serverName, ++lastRelayed, list.getPlayers());
        }

        if (event instanceof C2SServerStatus) {
            return status(origin);
        }

        return null;
    }

    /**
     * @param origin the roster entry of the server, as it is now.
     * @return a {@code serverStatus} built from it, with the next relay {@code seq}. For a status the client
     * reported and for one the hub sets itself, like {@code offline}.
     */
    S2CServerStatus status(S2CServerEntry origin) {
        return new S2CServerStatus(origin.getId(), origin.getName(), ++lastRelayed, origin.getState(),
                origin.getColour(), origin.getAddress(), origin.getMaxPlayers(), origin.getPlayers());
    }

    /**
     * @param id the server reconnecting.
     * @param after the client's {@code lastRelayed}.
     * @return every kept frame with {@code seq} above {@code after} that didn't come from {@code id}, oldest first.
     */
    List<S2CRelayed> missed(String id, long after) {
        List<S2CRelayed> missed = new ArrayList<>();
        for (S2CRelayed frame : kept) {
            if (frame.getSeq() > after && !frame.getServer().equals(id)) {
                missed.add(frame);
            }
        }
        return missed;
    }

    /** Adds the frame to the ring, dropping the oldest past {@link #KEPT}, and returns it. */
    private S2CRelayed keep(S2CRelayed frame) {
        kept.addLast(frame);
        while (kept.size() > KEPT) {
            kept.removeFirst();
        }
        return frame;
    }
}