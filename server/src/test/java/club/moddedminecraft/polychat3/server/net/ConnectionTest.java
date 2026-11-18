package club.moddedminecraft.polychat3.server.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.java_websocket.WebSocket;
import org.junit.jupiter.api.Test;

import club.moddedminecraft.polychat3.core.Protocol;
import club.moddedminecraft.polychat3.core.payloads.ProtocolError;

/** The hub keeps its own frames under {@link Protocol#MAX_FRAME_BYTES}, as in PROTOCOL.md "Transport and framing". */
class ConnectionTest {
    /** Frames handed to the socket. */
    private final List<String> sent = new ArrayList<>();
    /** A socket that only records send(String); every other method returns null. */
    private final WebSocket socket = (WebSocket) Proxy.newProxyInstance(WebSocket.class.getClassLoader(),
            new Class<?>[] { WebSocket.class }, (proxy, method, args) -> {
                if (method.getName().equals("send") && args != null && args[0] instanceof String) {
                    sent.add((String) args[0]);
                }
                return null;
            });
    private final Connection connection = new Connection(socket);

    @Test
    void sendsAFrameUnderTheLimit() {
        connection.send(new ProtocolError("x", text(100_000)));
        assertEquals(1, sent.size());
    }

    @Test
    void dropsAFrameOverTheLimit() {
        connection.send(new ProtocolError("x", text(Protocol.MAX_FRAME_BYTES)));
        assertEquals(0, sent.size());
    }

    /** The limit counts UTF-8 bytes: 400 000 two-byte characters are 800 000 bytes, under 1 MiB, but 600 000 are over. */
    @Test
    void countsBytesNotCharacters() {
        connection.send(new ProtocolError("x", text(400_000, 'é')));
        connection.send(new ProtocolError("x", text(600_000, 'é')));
        assertEquals(1, sent.size());
    }

    private static String text(int length) {
        return text(length, 'x');
    }

    private static String text(int length, char c) {
        char[] chars = new char[length];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
