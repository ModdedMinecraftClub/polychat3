package club.moddedminecraft.polychat3.server.net;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import club.moddedminecraft.polychat3.core.Protocol;
import club.moddedminecraft.polychat3.server.hub.Hub;

/** The WebSocket listener clients connect to. Handles sockets only and hands every event to the hub. */
public final class WebSocketEndpoint extends WebSocketServer {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketEndpoint.class);

    private final Hub hub;

    public WebSocketEndpoint(int port, Hub hub) {
        super(new InetSocketAddress(port), Collections.singletonList(new Draft_6455(
                Collections.emptyList(),
                Collections.singletonList(new org.java_websocket.protocols.Protocol("")),
                Protocol.MAX_FRAME_BYTES)));
        this.hub = hub;
        // Lets a restarted hub bind while the old connections are still in TIME_WAIT
        setReuseAddr(true);
    }

    @Override
    public void onStart() {
        LOG.info("WebSocket endpoint listening on port {}", getPort());
    }

    @Override
    public void onOpen(WebSocket socket, ClientHandshake request) {
        Connection connection = new Connection(socket);
        socket.setAttachment(connection);
        hub.opened(connection);
    }

    @Override
    public void onMessage(WebSocket socket, String frame) {
        hub.frame(socket.getAttachment(), frame);
    }

    @Override
    public void onMessage(WebSocket socket, ByteBuffer frame) {
        hub.binaryFrame(socket.getAttachment());
    }

    @Override
    public void onClose(WebSocket socket, int code, String reason, boolean remote) {
        Connection connection = socket.getAttachment();
        if (connection != null) {
            hub.closed(connection, code);
        }
    }

    @Override
    public void onError(WebSocket socket, Exception ex) {
        // Java-WebSocket only reports server-wide errors, such as the port being in use, without a connection
        if (socket == null) {
            LOG.error("Hub stopped", ex);
            System.exit(1);
        }
        Connection connection = socket.getAttachment();
        String who = connection != null ? connection.describe() : String.valueOf(socket.getRemoteSocketAddress());
        LOG.warn("Error on {}", who, ex);
    }
}
