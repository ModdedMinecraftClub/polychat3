package club.moddedminecraft.polychat3.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import club.moddedminecraft.polychat3.core.Protocol;
import club.moddedminecraft.polychat3.core.payloads.PayloadCodec;
import club.moddedminecraft.polychat3.core.payloads.PlayerInfo;
import club.moddedminecraft.polychat3.core.payloads.ProtocolException;
import club.moddedminecraft.polychat3.core.payloads.ServerState;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SHandshake;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CHandshakeAccepted;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerEntry;

class ClientSessionTest {
    private final RecordingTransport transport = new RecordingTransport();
    private final RecordingListener listener = new RecordingListener();
    private ScheduledExecutorService thread;
    private ClientSession session;

    @BeforeEach
    void createSession() {
        thread = Executors.newSingleThreadScheduledExecutor();
        session = new ClientSession("a5", "All the Mods 5", "secret", "yellow", "a5.example.net", 40, transport,
                listener, thread);
    }

    @AfterEach
    void stopThread() {
        thread.shutdownNow();
    }

    @Test
    void startConnectsOnce() throws Exception {
        session.start();
        sync();
        assertEquals(1, transport.connects);
        assertTrue(transport.sent.isEmpty());
    }

    @Test
    void openSendsHandshake() throws Exception {
        long before = System.currentTimeMillis();
        session.onOpen();
        sync();
        assertEquals(1, transport.sent.size());
        C2SHandshake handshake = handshake(transport.sent.get(0));
        assertEquals(Protocol.VERSION, handshake.getVersion());
        assertEquals("a5", handshake.getServer());
        assertEquals("All the Mods 5", handshake.getName());
        assertEquals("secret", handshake.getToken());
        assertTrue(handshake.getSession() <= before, "session is the time the session was created");
        assertEquals(ServerState.STARTING, handshake.getState());
        assertEquals("yellow", handshake.getColour());
        assertEquals("a5.example.net", handshake.getAddress());
        assertEquals(40, handshake.getMaxPlayers());
        assertEquals(0, handshake.getHubSession());
        assertEquals(0, handshake.getLastRelayed());
    }

    /** A reconnect keeps this process's session, so the hub keeps its lastSeq. */
    @Test
    void reconnectSendsTheSameSession() throws Exception {
        session.onOpen();
        session.onClose(1006, "");
        session.onOpen();
        sync();
        assertEquals(2, transport.sent.size());
        assertEquals(handshake(transport.sent.get(0)).getSession(), handshake(transport.sent.get(1)).getSession());
    }

    @Test
    void handshakeAcceptedGivesTheListenerTheServers() throws Exception {
        List<S2CServerEntry> servers = Collections.singletonList(new S2CServerEntry("gtnh", "GTNH", "gold",
                "gtnh.example.net", 30, ServerState.STARTED,
                Collections.singletonList(new PlayerInfo("Steve", "069a79f4-44e9-4726-a5be-fca90e38aaf5"))));
        session.onOpen();
        session.onFrame(PayloadCodec.encode(new S2CHandshakeAccepted(Protocol.VERSION, 1793120000000L, 0, servers)));
        sync();
        assertEquals(1, transport.sent.size(), "nothing but the handshake is sent yet");
        // S2CServerEntry has no equals, so compare the lists as JSON inside a frame
        assertEquals(PayloadCodec.encode(new S2CHandshakeAccepted(Protocol.VERSION, 0, 0, servers)),
                PayloadCodec.encode(new S2CHandshakeAccepted(Protocol.VERSION, 0, 0, listener.servers)));
    }

    /** An invalid frame is ignored for now (step 4 answers it), and the session keeps working. */
    @Test
    void invalidFrameIsIgnored() throws Exception {
        session.onOpen();
        session.onFrame("{");
        session.onFrame(PayloadCodec.encode(new S2CHandshakeAccepted(Protocol.VERSION, 1, 0,
                Collections.<S2CServerEntry>emptyList())));
        sync();
        assertEquals(1, transport.sent.size());
        assertEquals(Collections.emptyList(), listener.servers);
    }

    @Test
    void listenerHearsNothingBeforeAFrame() throws Exception {
        session.onOpen();
        sync();
        assertNull(listener.servers);
    }

    /** Waits until every task already posted to the session's thread has run. */
    private void sync() throws InterruptedException, ExecutionException {
        thread.submit(() -> {
        }).get();
    }

    private static C2SHandshake handshake(String frame) throws ProtocolException {
        return assertInstanceOf(C2SHandshake.class, PayloadCodec.C2S.decode(frame));
    }

    /** Records what the session asks of the socket. Written on the session's thread, read after {@link #sync}. */
    private static final class RecordingTransport implements Transport {
        int connects;
        final List<String> sent = new ArrayList<>();
        final List<Integer> closes = new ArrayList<>();

        @Override
        public void connect() {
            connects++;
        }

        @Override
        public void send(String frame) {
            sent.add(frame);
        }

        @Override
        public void close(int code) {
            closes.add(code);
        }
    }

    /** Records what the session hands the mod. Written on the session's thread, read after {@link #sync}. */
    private static final class RecordingListener implements MessageListener {
        List<S2CServerEntry> servers;

        @Override
        public void servers(List<S2CServerEntry> servers) {
            this.servers = servers;
        }
    }
}
