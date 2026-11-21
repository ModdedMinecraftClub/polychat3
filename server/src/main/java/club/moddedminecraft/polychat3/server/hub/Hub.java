package club.moddedminecraft.polychat3.server.hub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import club.moddedminecraft.polychat3.core.Protocol;
import club.moddedminecraft.polychat3.core.payloads.Payload;
import club.moddedminecraft.polychat3.core.payloads.PayloadCodec;
import club.moddedminecraft.polychat3.core.payloads.ProtocolError;
import club.moddedminecraft.polychat3.core.payloads.ProtocolException;
import club.moddedminecraft.polychat3.core.payloads.ServerState;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SChatMessage;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SCommandResult;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SEvent;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SHandshake;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SMute;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SServerStatus;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CAcknowledgement;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CHandshakeAccepted;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CMute;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CMuteList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CRelayed;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CRunCommand;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CSystemMessage;
import club.moddedminecraft.polychat3.server.net.Connection;

/**
 * The core of the polychat server. Every event from the endpoint runs on one thread here, so the hub's
 * state needs no locks. Accepts or refuses each connection's handshake, then acknowledges its events and relays
 * them to the other game servers, and keeps the network's mute list, as described in PROTOCOL.md.
 */
public final class Hub {
    private static final Logger LOG = LoggerFactory.getLogger(Hub.class);
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 5;
    private static final int MAX_INVALID_FRAMES = 3;
    /** How often the hub looks for servers that went quiet. */
    private static final long QUIET_CHECK_SECONDS = 5;
    /** How often the hub fails commands that got no answer in time. */
    private static final long EXPIRE_CHECK_SECONDS = 1;

    private final byte[] token;
    /** Sent in {@code handshakeAccepted}, so clients know a restarted hub has nothing to replay. */
    private final long session = System.currentTimeMillis();
    /** Accepted connections, by server id. */
    private final Map<String, Connection> servers = new HashMap<>();
    /** The last accepted {@code seq} for each game server, kept across its connections. */
    private final SequenceTracker sequences = new SequenceTracker();
    /** Builds the frames to relay to other game servers, numbered with the hub's relay {@code seq}. */
    private final Relay relay = new Relay();
    /** Each game server's details, state and players, as of its latest handshake and events. */
    private final Roster roster = new Roster();
    /** Who is muted across the network, saved to a file on every change. */
    private final Mutes mutes;
    /** The commands sent to game servers with {@code runCommand} that have no answer yet. */
    private final CommandBroker commands;
    /** The hub thread. Everything the hub does runs on it. */
    private final ScheduledExecutorService thread = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread hubThread = new Thread(runnable, "polychat-hub");
        // The endpoint's threads keep the process alive; this one shouldn't block exit
        hubThread.setDaemon(true);
        return hubThread;
    });

    /**
     * @param token the shared {@code server_token} every client must send in its handshake.
     * @param mutesFile where the mute list is kept; a missing file means nobody is muted.
     * @param commandTimeoutSeconds how long a game server has to answer a {@code runCommand}.
     * @throws IOException if the mute list can't be read.
     */
    public Hub(String token, Path mutesFile, int commandTimeoutSeconds) throws IOException {
        this.token = token.getBytes(StandardCharsets.UTF_8);
        this.mutes = new Mutes(mutesFile);
        mutes.load();
        commands = new CommandBroker(TimeUnit.SECONDS.toNanos(commandTimeoutSeconds));
        thread.scheduleAtFixedRate(logged(this::checkQuiet), QUIET_CHECK_SECONDS, QUIET_CHECK_SECONDS,
                TimeUnit.SECONDS);
        thread.scheduleAtFixedRate(logged(() -> commands.expire(System.nanoTime())), EXPIRE_CHECK_SECONDS,
                EXPIRE_CHECK_SECONDS, TimeUnit.SECONDS);
    }

    /** A socket opened. Starts the handshake timeout. */
    public void opened(Connection connection) {
        thread.schedule(logged(() -> {
            if (connection.getState() == Connection.State.AWAITING_HANDSHAKE) {
                refuse(connection, Protocol.CLOSE_BAD_HANDSHAKE, "handshake_timeout",
                        "no handshake within " + HANDSHAKE_TIMEOUT_SECONDS + " s");
            }
        }), HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** A text frame arrived. */
    public void frame(Connection connection, String frame) {
        thread.execute(logged(() -> {
            switch (connection.getState()) {
                case AWAITING_HANDSHAKE:
                    handshake(connection, frame);
                    break;
                case ACTIVE:
                    receive(connection, frame);
                    break;
                case CLOSING:
                    // Refused or timed out; the socket is closing
                    break;
            }
        }));
    }

    /** A binary frame arrived; the protocol forbids them. */
    public void binaryFrame(Connection connection) {
        thread.execute(logged(() -> {
            refuse(connection, Protocol.CLOSE_PROTOCOL_ERROR, "binary_frame", "binary frames are not allowed");
        }));
    }

    /**
     * A socket closed. If it was still the live connection for its server id, forgets it and marks the server
     * {@code offline} for the other game servers, as in PROTOCOL.md "Shutting down". Fails the commands still
     * waiting on it.
     */
    public void closed(Connection connection, int code) {
        thread.execute(logged(() -> {
            connection.setState(Connection.State.CLOSING);
            C2SHandshake handshake = connection.getHandshake();
            if (handshake != null && servers.remove(handshake.getServer(), connection)) {
                String id = handshake.getServer();
                LOG.info("{} disconnected with {}", handshake.getServer(), code);
                commands.fail(id, id + " went offline");
                if (roster.disconnected(id)) {
                    broadcast(relay.status(roster.entry(id)), connection);
                }
            }
        }));
    }

    /**
     * Sends a systemMessage to every connected game server, for a module. Safe from any thread: it only posts to the
     * hub thread.
     */
    public void systemMessage(String message, S2CSystemMessage.Kind kind) {
        thread.execute(logged(() -> {
            S2CSystemMessage frame = new S2CSystemMessage(message, kind);
            broadcast(frame, null);
        }));
    }

    /**
     * Runs a module's task on the hub thread every period, the first time one period from now. An exception is logged
     * and the task keeps running.
     *
     * @return the task's handle; its cancel(false) stops it.
     */
    public ScheduledFuture<?> every(long period, TimeUnit unit, Runnable task) {
        return thread.scheduleAtFixedRate(logged(task), period, period, unit);
    }

    /**
     * Sends a runCommand to one game server, for a module. Safe from any thread: it only posts to the hub thread.
     *
     * @param server the game server's id, for example {@code a5}.
     * @param name the command's name, which the server may map to its own command.
     * @param defaultCommand the console command to run if the server has no mapping for name; $args is replaced by
     *        args.
     * @param args joined with spaces in place of $args.
     * @return completed on the hub thread with the server's commandResult, or exceptionally with CommandFailed if
     *         the server isn't connected, is unresponsive, goes away or doesn't answer in time.
     */
    public CompletableFuture<C2SCommandResult> runCommand(String server, String name, String defaultCommand,
            List<String> args) {
        CompletableFuture<C2SCommandResult> result = new CompletableFuture<>();
        List<String> argsCopy = new ArrayList<>(args);
        thread.execute(logged(() -> {
            Connection connection = servers.get(server);
            if (connection == null || connection.getState() != Connection.State.ACTIVE) {
                result.completeExceptionally(new CommandFailed(server + " is not connected"));
                return;
            } else if (roster.entry(server).getState() == ServerState.UNRESPONSIVE) {
                result.completeExceptionally(new CommandFailed(server + " is unresponsive"));
                return;
            }
            String id = commands.start(server, System.nanoTime(), result);
            connection.send(new S2CRunCommand(id, name, defaultCommand, argsCopy));
        }));
        return result;
    }

    /** Sends a protocolError saying why, then closes with closeCode. */
    private void refuse(Connection connection, int closeCode, String code, String message) {
        if (connection.getState() == Connection.State.CLOSING || !connection.isOpen()) {
            return;
        }
        connection.setState(Connection.State.CLOSING);
        LOG.warn("Closing {} with {}: {}", connection.describe(), closeCode, message);
        connection.send(new ProtocolError(code, message));
        // No close reason: it is limited to 123 bytes, and the protocolError already says why
        connection.close(closeCode);
    }

    /** Sends a frame to every active game server except origin, or to every one if origin is null. */
    private void broadcast(Payload frame, Connection origin) {
        for (Connection other : servers.values()) {
            if (other != origin && other.getState() == Connection.State.ACTIVE) {
                other.send(frame);
            }
        }
    }

    /** Wraps a hub task so an exception is logged instead of silently lost by the executor. */
    private static Runnable logged(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOG.error("Hub task failed", e);
            }
        };
    }

    /**
     * A frame after the handshake. Acknowledges every event, as in PROTOCOL.md "Sequence numbers,
     * acknowledgements and replay". An event that isn't a duplicate is first relayed to every other game server,
     * and a {@code commandResult} completes the command it answers. Any valid frame counts as hearing from the
     * server and brings an {@code unresponsive} one back. Closes the connection after too many invalid frames in a
     * row.
     */
    private void receive(Connection connection, String frame) {
        Payload payload;
        try {
            payload = PayloadCodec.C2S.decode(frame);
        } catch (ProtocolException e) {
            if (connection.invalidFrame() >= MAX_INVALID_FRAMES) {
                refuse(connection, Protocol.CLOSE_PROTOCOL_ERROR, "protocol_error",
                        MAX_INVALID_FRAMES + " invalid frames in a row, last: " + e.getMessage());
            } else {
                LOG.warn("Invalid frame from {}: {}", connection.describe(), e.getMessage());
                connection.send(new ProtocolError("invalid_frame", e.getMessage()));
            }
            return;
        }
        connection.validFrame();
        String id = connection.getHandshake().getServer();
        if (roster.heard(id, System.nanoTime())) {
            broadcast(relay.status(roster.entry(id)), connection);
        }
        if (!(payload instanceof C2SEvent)) {
            // heartbeat and protocolError aren't events, so no ack
            return;
        }
        C2SEvent event = (C2SEvent) payload;
        if (sequences.accept(id, event.getSeq()) && !mutedChat(id, event) && !hubOnlyStatus(id, event)) {
            roster.update(id, event);
            S2CRelayed relayed = relay.relayed(roster.entry(id), event);
            if (relayed != null) {
                broadcast(relayed, connection);
            }
            if (event instanceof C2SMute) {
                mute(id, (C2SMute) event);
            }
            if (event instanceof C2SCommandResult) {
                commands.result(id, (C2SCommandResult) event);
            }
        }
        connection.send(new S2CAcknowledgement(sequences.last(id)));
    }

    /**
     * Runs every {@link #QUIET_CHECK_SECONDS} on the hub thread. Marks each server that sent no frame for
     * {@link Roster#QUIET_SECONDS} {@code unresponsive}, as in PROTOCOL.md "Keepalive", tells the other game
     * servers and fails the commands still waiting on it.
     */
    private void checkQuiet() {
        for (String id : roster.unresponsive(System.nanoTime())) {
            LOG.warn("{} unresponsive: no frame for {} s", id, Roster.QUIET_SECONDS);
            broadcast(relay.status(roster.entry(id)), servers.get(id));
            commands.fail(id, id + " is unresponsive");
        }
    }

    /**
     * A staff member muted or unmuted a player, as in PROTOCOL.md "Mutes". A change goes to every game server,
     * including the one it came from.
     */
    private void mute(String id, C2SMute event) {
        String target;
        try {
            target = Mutes.normalise(event.getTarget());
            if (mutes.set(target, event.isMuted())) {
                LOG.info("{} {} {} on {}", event.getBy(), event.isMuted() ? "muted" : "unmuted", target, id);
                broadcast(new S2CMute(target, event.isMuted(), event.getBy()), null);
            }
        } catch (IllegalArgumentException e) {
            LOG.warn("Ignoring a mute from {}: target {} is not a UUID", id, event.getTarget());
        }
    }

    /**
     * @return true if event is chat from a muted player, which the hub logs and doesn't relay, as in PROTOCOL.md
     * "Mutes". It means the client's copy of the mute list is behind.
     */
    private boolean mutedChat(String id, C2SEvent event) {
        if (!(event instanceof C2SChatMessage)) {
            return false;
        }
        C2SChatMessage chat = (C2SChatMessage) event;
        if (!(mutes.isMuted(chat.getSenderId()))) {
            return false;
        }
        LOG.info("Dropped chat from muted {} ({}) on {}: {}", chat.getPlayer(), chat.getSenderId(), id,
                chat.getMessage());
        return true;
    }

    /**
     * @return true if event is a {@code serverStatus} with a state only the hub sets, which the hub logs and
     * doesn't apply or relay, as in PROTOCOL.md "Field types". It is still acknowledged, so the client stops
     * resending it.
     */
    private boolean hubOnlyStatus(String id, C2SEvent event) {
        if (!(event instanceof C2SServerStatus)) {
            return false;
        }
        C2SServerStatus status = (C2SServerStatus) event;
        if (!status.getState().hubOnly()) {
            return false;
        }
        LOG.warn("Ignoring serverStatus {} from {}: only the hub sets it", status.getState(), id);
        return true;
    }

    /** The first frame. Accepts or refuses the handshake, as in PROTOCOL.md "Handshake". */
    private void handshake(Connection connection, String frame) {
        Payload payload;
        try {
            payload = PayloadCodec.C2S.decode(frame);
        } catch (ProtocolException e) {
            refuse(connection, Protocol.CLOSE_BAD_HANDSHAKE, "bad_handshake", e.getMessage());
            return;
        }
        if (!(payload instanceof C2SHandshake)) {
            refuse(connection, Protocol.CLOSE_BAD_HANDSHAKE, "bad_handshake",
                    "expected handshake, got " + payload.type());
            return;
        }

        C2SHandshake handshake = (C2SHandshake) payload;
        String id = handshake.getServer();
        if (handshake.getState().hubOnly()) {
            refuse(connection, Protocol.CLOSE_BAD_HANDSHAKE, "bad_handshake",
                    "client sent hub-only state " + handshake.getState());
            return;
        }
        if (!Protocol.SERVER_ID.matcher(id).matches()) {
            refuse(connection, Protocol.CLOSE_BAD_SERVER_ID, "bad_server_id", "invalid server id " + id);
            return;
        }
        if (!MessageDigest.isEqual(token, handshake.getToken().getBytes(StandardCharsets.UTF_8))) {
            refuse(connection, Protocol.CLOSE_BAD_TOKEN, "bad_token", "wrong token for " + id);
            return;
        }
        if (handshake.getVersion() != Protocol.VERSION) {
            refuse(connection, Protocol.CLOSE_BAD_VERSION, "unsupported_version",
                    "hub uses version " + Protocol.VERSION + ", " + id + " uses " + handshake.getVersion());
            return;
        }

        connection.setHandshake(handshake);
        connection.setState(Connection.State.ACTIVE);
        roster.connected(handshake, System.nanoTime());
        Connection old = servers.put(id, connection);
        if (old != null) {
            refuse(old, Protocol.CLOSE_REPLACED, "replaced", "replaced by a newer connection for " + id);
        }
        // The other servers last saw this one offline or unresponsive, or never saw it, as after a hub restart
        broadcast(relay.status(roster.entry(id)), connection);
        // lastSeq lets the client resend what the hub missed; servers is every other game server's current
        // state
        connection
                .send(new S2CHandshakeAccepted(Protocol.VERSION, session, sequences.resume(id, handshake.getSession()),
                        roster.others(id)));
        // Replay what the client missed while it was away, as in PROTOCOL.md "Replay to the client". A first
        // connection sends hubSession 0 and a restarted hub has a new session, so neither replays
        if (handshake.getHubSession() == session) {
            for (S2CRelayed missed : relay.missed(id, handshake.getLastRelayed())) {
                connection.send(missed);
            }
        }
        // Replaces the client's copy, so it can stop a muted player's chat on its own server
        connection.send(new S2CMuteList(mutes.list()));
        LOG.info("{} ({}) connected from {}", id, handshake.getName(), connection.remoteAddress());
    }
}
