package club.moddedminecraft.polychat3.server;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import club.moddedminecraft.polychat3.core.Protocol;
import club.moddedminecraft.polychat3.core.payloads.Payload;
import club.moddedminecraft.polychat3.core.payloads.PayloadCodec;
import club.moddedminecraft.polychat3.core.payloads.ProtocolError;
import club.moddedminecraft.polychat3.core.payloads.ProtocolException;
import club.moddedminecraft.polychat3.core.payloads.c2s.Handshake;
import club.moddedminecraft.polychat3.core.payloads.s2c.HandshakeAccepted;
import club.moddedminecraft.polychat3.core.payloads.s2c.MuteList;

/**
 * The WebSocket listener game servers connect to. Accepts or refuses each connection's
 * handshake, as described in PROTOCOL.md.
 */
public class Hub extends WebSocketServer {
    private static final Logger LOG = LoggerFactory.getLogger(Hub.class);
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 5;
    private static final int MAX_INVALID_FRAMES = 3;

    private final byte[] token;
    /** Sent in {@code handshakeAccepted}, so clients know a restarted hub has nothing to replay. */
    private final long session = System.currentTimeMillis();
    /** Accepted connections, by server id. */
    private final Map<String, WebSocket> servers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeouts = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "polychat-handshake-timeout");
        thread.setDaemon(true);
        return thread;
    });

    /** What the hub knows about one connection, kept as its attachment. */
    private static final class Client {
        /** Set by the first frame or the handshake timeout, whichever comes first. */
        final AtomicBoolean firstFrame = new AtomicBoolean();
        /** Null until the handshake is accepted. */
        volatile Handshake handshake;
        int invalidFrames;
    }

    public Hub(int port, String token) {
        super(new InetSocketAddress(port), Collections.singletonList(new Draft_6455(
                Collections.emptyList(),
                Collections.singletonList(new org.java_websocket.protocols.Protocol("")),
                Protocol.MAX_FRAME_BYTES)));
        this.token = token.getBytes(StandardCharsets.UTF_8);
        // Lets a restarted hub bind while the old connections are still in TIME_WAIT
        setReuseAddr(true);
    }

    @Override
    public void onStart() {
        LOG.info("Listening on port {}", getPort());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake request) {
        Client client = new Client();
        conn.setAttachment(client);
        timeouts.schedule(() -> {
            if (client.firstFrame.compareAndSet(false, true)) {
                refuse(conn, Protocol.CLOSE_BAD_HANDSHAKE, "handshake_timeout",
                        "no handshake within " + HANDSHAKE_TIMEOUT_SECONDS + " s");
            }
        }, HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void onMessage(WebSocket conn, String frame) {
        Client client = conn.getAttachment();
        if (client.firstFrame.compareAndSet(false, true)) {
            handshake(conn, client, frame);
        } else if (client.handshake != null) {
            receive(conn, client, frame);
        }
        // Otherwise the handshake timed out or was refused, and the connection is closing
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer frame) {
        Client client = conn.getAttachment();
        client.firstFrame.set(true);
        refuse(conn, Protocol.CLOSE_PROTOCOL_ERROR, "binary_frame", "binary frames are not allowed");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Client client = conn.getAttachment();
        if (client != null && client.handshake != null && servers.remove(client.handshake.getServer(), conn)) {
            LOG.info("{} disconnected with {}", client.handshake.getServer(), code);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception e) {
        if (conn == null) {
            // Java-WebSocket only reports server-wide errors, such as the port being in use, without a connection
            LOG.error("Hub stopped", e);
            System.exit(1);
        }
        LOG.warn("Error on {}", describe(conn), e);
    }

    private void handshake(WebSocket conn, Client client, String frame) {
        Payload payload;
        try {
            payload = PayloadCodec.C2S.decode(frame);
        } catch (ProtocolException e) {
            refuse(conn, Protocol.CLOSE_BAD_HANDSHAKE, "bad_handshake", e.getMessage());
            return;
        }
        if (!(payload instanceof Handshake)) {
            refuse(conn, Protocol.CLOSE_BAD_HANDSHAKE, "bad_handshake", "expected handshake, got " + payload.type());
            return;
        }

        Handshake handshake = (Handshake) payload;
        String id = handshake.getServer();
        if (!Protocol.SERVER_ID.matcher(id).matches()) {
            refuse(conn, Protocol.CLOSE_BAD_SERVER_ID, "bad_server_id", "invalid server id " + id);
            return;
        }
        if (!MessageDigest.isEqual(token, handshake.getToken().getBytes(StandardCharsets.UTF_8))) {
            refuse(conn, Protocol.CLOSE_BAD_TOKEN, "bad_token", "wrong token for " + id);
            return;
        }
        if (handshake.getVersion() != Protocol.VERSION) {
            refuse(conn, Protocol.CLOSE_BAD_VERSION, "unsupported_version",
                    "hub uses version " + Protocol.VERSION + ", " + id + " uses " + handshake.getVersion());
            return;
        }

        client.handshake = handshake;
        WebSocket old = servers.put(id, conn);
        if (old != null) {
            refuse(old, Protocol.CLOSE_REPLACED, "replaced", "replaced by a newer connection for " + id);
        }
        // The hub doesn't track events or other servers yet, so there is nothing to resume, list or replay
        conn.send(PayloadCodec.encode(new HandshakeAccepted(Protocol.VERSION, session, 0,
                Collections.emptyList())));
        // No mute list is kept yet either
        conn.send(PayloadCodec.encode(new MuteList(Collections.emptyList())));
        LOG.info("{} ({}) connected from {}", id, handshake.getName(), conn.getRemoteSocketAddress());
    }

    private void receive(WebSocket conn, Client client, String frame) {
        try {
            PayloadCodec.C2S.decode(frame);
        } catch (ProtocolException e) {
            client.invalidFrames++;
            if (client.invalidFrames >= MAX_INVALID_FRAMES) {
                refuse(conn, Protocol.CLOSE_PROTOCOL_ERROR, "protocol_error",
                        MAX_INVALID_FRAMES + " invalid frames in a row, last: " + e.getMessage());
            } else {
                LOG.warn("Invalid frame from {}: {}", describe(conn), e.getMessage());
                conn.send(PayloadCodec.encode(new ProtocolError("invalid_frame", e.getMessage())));
            }
            return;
        }
        client.invalidFrames = 0;
        // The hub doesn't act on events yet
    }

    /** Sends a protocolError saying why, then closes with closeCode. */
    private static void refuse(WebSocket conn, int closeCode, String code, String message) {
        if (!conn.isOpen()) {
            return;
        }
        LOG.warn("Closing {} with {}: {}", describe(conn), closeCode, message);
        conn.send(PayloadCodec.encode(new ProtocolError(code, message)));
        // No close reason: it is limited to 123 bytes, and the protocolError already says why
        conn.close(closeCode);
    }

    private static String describe(WebSocket conn) {
        Client client = conn.getAttachment();
        if (client != null && client.handshake != null) {
            return client.handshake.getServer();
        }
        return String.valueOf(conn.getRemoteSocketAddress());
    }
}
