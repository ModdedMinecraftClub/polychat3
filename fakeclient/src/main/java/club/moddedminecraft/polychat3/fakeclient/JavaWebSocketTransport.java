package club.moddedminecraft.polychat3.fakeclient;

import java.net.URI;
import java.util.Collections;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;

import club.moddedminecraft.polychat3.core.Protocol;
import club.moddedminecraft.polychat3.core.client.ClientSession;
import club.moddedminecraft.polychat3.core.client.Transport;

/**
 * A {@link Transport} on Java-WebSocket: the worked example for the mod. Every callback only passes its event to the
 * session, which does the work on its own thread.
 */
public final class JavaWebSocketTransport implements Transport {
    // PROTOCOL.md "Transport and framing": a frame over MAX_FRAME_BYTES closes the connection with 1009. Java-WebSocket
    // copies the draft for every connection, so one instance serves them all
    private static final Draft Draft_6455 = new Draft_6455(Collections.emptyList(),
            Collections.singletonList(new org.java_websocket.protocols.Protocol("")),
            Protocol.MAX_FRAME_BYTES);
    private final URI uri;
    // Set once, before the session starts
    private ClientSession session;
    // The current connection; every connect() makes a new one. Only used on the session's thread
    private WebSocketClient client;

    public JavaWebSocketTransport(URI uri) {
        this.uri = uri;
    }

    /** The session and the transport each need the other, so the session is set right after both are built. */
    public void setSession(ClientSession session) {
        this.session = session;
    }

    @Override
    public void connect() {
        client = new WebSocketClient(uri, Draft_6455) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                session.onOpen();
            }

            @Override
            public void onMessage(String message) {
                session.onFrame(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                session.onClose(code, reason);
            }

            @Override
            public void onError(Exception e) {
                System.err.println("[polychat] socket error: " + e);
            }
        };
        // PROTOCOL.md "Keepalive": ping every 30 s, close with 1006 if no pong within 45 s
        client.setConnectionLostTimeout(30);
        client.connect();
    }

    @Override
    public void send(String frame) {
        try {
            client.send(frame);
        } catch (WebsocketNotConnectedException e) {
            // Closed meanwhile; onClose follows and the session handles it
        }
    }

    @Override
    public void close(int code) {
        if (client != null) {
            client.close(code);
        }
    }
}
