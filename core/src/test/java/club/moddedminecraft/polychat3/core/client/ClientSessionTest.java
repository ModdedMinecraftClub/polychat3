package club.moddedminecraft.polychat3.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import club.moddedminecraft.polychat3.core.Protocol;
import club.moddedminecraft.polychat3.core.payloads.Payload;
import club.moddedminecraft.polychat3.core.payloads.PayloadCodec;
import club.moddedminecraft.polychat3.core.payloads.PlayerInfo;
import club.moddedminecraft.polychat3.core.payloads.ProtocolException;
import club.moddedminecraft.polychat3.core.payloads.ServerState;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SEvent;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SHandshake;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CAcknowledgement;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CHandshakeAccepted;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerEntry;

class ClientSessionTest {
    private final RecordingTransport transport = new RecordingTransport();
    private final RecordingListener listener = new RecordingListener();
    /** What the mod would say is online; the session asks for it after every handshakeAccepted. */
    private final List<PlayerInfo> online = new ArrayList<>();
    private ScheduledExecutorService thread;
    private ClientSession session;

    @BeforeEach
    void createSession() {
        thread = Executors.newSingleThreadScheduledExecutor();
        session = new ClientSession("a5", "All the Mods 5", "secret", "yellow", "a5.example.net", 40, transport,
                listener, () -> new ArrayList<>(online), thread);
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
        assertEquals(Collections.singletonList("playerList 1"), eventsSent());
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
        assertEquals(Collections.singletonList("playerList 1"), eventsSent());
        assertEquals(Collections.emptyList(), listener.servers);
    }

    @Test
    void listenerHearsNothingBeforeAFrame() throws Exception {
        session.onOpen();
        sync();
        assertNull(listener.servers);
    }

    /** Every event gets the next seq, from 1, whatever its type. The playerList after handshakeAccepted is 1. */
    @Test
    void eventsAreNumberedFromOne() throws Exception {
        accept();
        session.chatMessage("Alex", "ec561538-f3fd-461d-aff5-086b22154bce", "hi");
        session.playerJoin("Bob", "b0b0b0b0-0000-0000-0000-000000000000", 2);
        session.commandResult("c1", true, "done", false);
        session.serverStatus(ServerState.STARTED);
        sync();
        assertEquals(Arrays.asList("playerList 1", "chatMessage 2", "playerJoin 3", "commandResult 4", "serverStatus 5"),
                eventsSent());
    }

    /** Before handshakeAccepted the session sends nothing but the handshake; events wait for the resend. */
    @Test
    void eventsWaitUntilAccepted() throws Exception {
        session.onOpen();
        session.chatMessage("Alex", "ec561538-f3fd-461d-aff5-086b22154bce", "hi");
        sync();
        assertEquals(1, transport.sent.size());
    }

    /** A closed connection is no longer accepted, so events made while it is down aren't sent. */
    @Test
    void eventsWaitAfterAClose() throws Exception {
        accept();
        session.onClose(1006, "");
        session.chatMessage("Alex", "ec561538-f3fd-461d-aff5-086b22154bce", "hi");
        sync();
        assertEquals(Collections.singletonList("playerList 1"), eventsSent());
    }

    @Test
    void serverStatusGoesInTheNextHandshake() throws Exception {
        accept();
        session.serverStatus(ServerState.STARTED);
        session.onClose(1006, "");
        session.onOpen();
        sync();
        assertEquals(ServerState.STARTED, handshake(transport.sent.get(transport.sent.size() - 1)).getState());
    }

    /** After a reconnect the session resends what the hub didn't acknowledge, oldest first, then the players. */
    @Test
    void reconnectResendsWhatWasNotAcknowledged() throws Exception {
        accept();
        chats(3);
        session.onFrame(PayloadCodec.encode(new S2CAcknowledgement(3)));
        reconnect(0);
        assertEquals(Arrays.asList("chatMessage 4", "playerList 5"), eventsSent());
    }

    /** The hub's lastSeq covers events it got but never acknowledged before the drop. */
    @Test
    void reconnectSkipsWhatTheHubAlreadyHas() throws Exception {
        accept();
        chats(3);
        reconnect(3);
        assertEquals(Arrays.asList("chatMessage 4", "playerList 5"), eventsSent());
    }

    @Test
    void commandResultAndPlayerListAreNotResent() throws Exception {
        accept();
        session.commandResult("c1", true, "done", false);
        chats(1);
        reconnect(1);
        assertEquals(Arrays.asList("chatMessage 3", "playerList 4"), eventsSent());
    }

    /** Events made before the first handshakeAccepted wait; past 200 the oldest is dropped. */
    @Test
    void bufferKeepsTheNewest200() throws Exception {
        session.onOpen();
        chats(201);
        session.onFrame(PayloadCodec.encode(new S2CHandshakeAccepted(Protocol.VERSION, 1, 0,
                Collections.<S2CServerEntry>emptyList())));
        List<String> sent = eventsSent();
        assertEquals(201, sent.size());
        assertEquals("chatMessage 2", sent.get(0));
        assertEquals("chatMessage 201", sent.get(199));
        assertEquals("playerList 202", sent.get(200));
    }

    @Test
    void playerListCarriesTheModsPlayers() throws Exception {
        online.add(new PlayerInfo("Steve", "069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        accept();
        sync();
        C2SPlayerList list = assertInstanceOf(C2SPlayerList.class,
                PayloadCodec.C2S.decode(transport.sent.get(transport.sent.size() - 1)));
        assertEquals(1, list.getPlayers().size());
        assertEquals("Steve", list.getPlayers().get(0).getName());
    }

    /** Opens the connection and accepts it, as a hub with nothing to resend or list would. */
    private void accept() {
        session.onOpen();
        session.onFrame(PayloadCodec.encode(new S2CHandshakeAccepted(Protocol.VERSION, 1, 0,
                Collections.<S2CServerEntry>emptyList())));
    }

    /** Sends count chat lines from the game. */
    private void chats(int count) {
        for (int i = 0; i < count; i++) {
            session.chatMessage("Alex", "ec561538-f3fd-461d-aff5-086b22154bce", "line " + i);
        }
    }

    /** Drops the connection, forgets what was sent so far, and reconnects to a hub that has up to lastSeq. */
    private void reconnect(long lastSeq) throws Exception {
        session.onClose(1006, "");
        sync();
        transport.sent.clear();
        session.onOpen();
        session.onFrame(PayloadCodec.encode(new S2CHandshakeAccepted(Protocol.VERSION, 1, lastSeq,
                Collections.<S2CServerEntry>emptyList())));
    }

    /** Every event sent so far, as "type seq", leaving out the handshake. */
    private List<String> eventsSent() throws Exception {
        sync();
        List<String> events = new ArrayList<>();
        for (String frame : transport.sent) {
            Payload payload = PayloadCodec.C2S.decode(frame);
            if (payload instanceof C2SEvent) {
                events.add(payload.type() + " " + ((C2SEvent) payload).getSeq());
            }
        }
        return events;
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
