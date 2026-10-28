package club.moddedminecraft.polychat3.server.hub;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import club.moddedminecraft.polychat3.core.Protocol;
import club.moddedminecraft.polychat3.core.payloads.Payload;
import club.moddedminecraft.polychat3.core.payloads.PayloadCodec;
import club.moddedminecraft.polychat3.core.payloads.ProtocolError;
import club.moddedminecraft.polychat3.core.payloads.ProtocolException;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SEvent;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SHandshake;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CAcknowledgement;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CHandshakeAccepted;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CMuteList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CRelayed;
import club.moddedminecraft.polychat3.server.net.Connection;

/**
 * The core of the polychat server. Every event from the endpoint runs on one thread here, so the hub's
 * state needs no locks. Accepts or refuses each connection's handshake, then acknowledges its events and relays
 * them to the other game servers, as described in PROTOCOL.md.
 */
public final class Hub {
    private static final Logger LOG = LoggerFactory.getLogger(Hub.class);
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 5;
    private static final int MAX_INVALID_FRAMES = 3;

    private final byte[] token;
    /** Sent in {@code handshakeAccepted}, so clients know a restarted hub has nothing to replay. */
    private final long session = System.currentTimeMillis();
    /** Accepted connections, by server id. */
    private final Map<String, Connection> servers = new HashMap<>();
    /** The last accepted {@code seq} for each game server, kept across its connections. */
    private final SequenceTracker sequences = new SequenceTracker();
    /** Builds the frames to relay to other game servers, numbered with the hub's relay {@code seq}. */
    private final Relay relay = new Relay();
    /** The hub thread. Everything the hub does runs on it. */
    private final ScheduledExecutorService thread = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread hubThread = new Thread(runnable, "polychat-hub");
        // The endpoint's threads keep the process alive; this one shouldn't block exit
        hubThread.setDaemon(true);
        return hubThread;
    });

    /** @param token the shared {@code server_token} every client must send in its handshake. */
    public Hub(String token) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
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

    /** A socket closed. Forgets it if it was still the live connection for its server id. */
    public void closed(Connection connection, int code) {
        thread.execute(logged(() -> {
            connection.setState(Connection.State.CLOSING);
            C2SHandshake handshake = connection.getHandshake();
            if (handshake != null && servers.remove(handshake.getServer(), connection)) {
                LOG.info("{} disconnected with {}", handshake.getServer(), code);
            }
        }));
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
     * acknowledgements and replay". An event that isn't a duplicate is first relayed to every other game server.
     * Closes the connection after too many invalid frames in a row.
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
        if (!(payload instanceof C2SEvent)) {
            // heartbeat and protocolError aren't events, so no ack
            return;
        }
        C2SEvent event = (C2SEvent) payload;
        String id = connection.getHandshake().getServer();
        if (sequences.accept(id, event.getSeq())) {
            C2SHandshake origin = connection.getHandshake();
            S2CRelayed relayed = relay.relayed(id, origin.getName(), event);
            if (relayed != null) {
                for (Connection other : servers.values()) {
                    if (other != connection && other.getState() == Connection.State.ACTIVE) {
                        other.send(relayed);
                    }
                }
            }
        }
        connection.send(new S2CAcknowledgement(sequences.last(id)));
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
        Connection old = servers.put(id, connection);
        if (old != null) {
            refuse(old, Protocol.CLOSE_REPLACED, "replaced", "replaced by a newer connection for " + id);
        }
        // lastSeq lets the client resend what the hub missed. The hub doesn't track other servers or keep
        // relayed frames yet, so there is nothing to list or replay
        connection
                .send(new S2CHandshakeAccepted(Protocol.VERSION, session, sequences.resume(id, handshake.getSession()),
                        Collections.emptyList()));
        // No mute list is kept yet either
        connection.send(new S2CMuteList(Collections.emptyList()));
        LOG.info("{} ({}) connected from {}", id, handshake.getName(), connection.remoteAddress());
    }
}
