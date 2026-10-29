package club.moddedminecraft.polychat3.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import club.moddedminecraft.polychat3.core.Protocol;
import club.moddedminecraft.polychat3.core.payloads.Payload;
import club.moddedminecraft.polychat3.core.payloads.PayloadCodec;
import club.moddedminecraft.polychat3.core.payloads.PlayerInfo;
import club.moddedminecraft.polychat3.core.payloads.ProtocolError;
import club.moddedminecraft.polychat3.core.payloads.ProtocolException;
import club.moddedminecraft.polychat3.core.payloads.ServerState;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SEvent;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SHandshake;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CAcknowledgement;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CChatMessage;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CHandshakeAccepted;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CMute;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CMuteList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CRelayed;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CRunCommand;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerEntry;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerStatus;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CSystemMessage;

class ClientSessionTest {
    private final RecordingTransport transport = new RecordingTransport();
    private final RecordingListener listener = new RecordingListener();
    /** What the mod would say is online; the session asks for it after every handshakeAccepted. */
    private final List<PlayerInfo> online = new ArrayList<>();
    private RecordingExecutor thread;
    private ClientSession session;

    @BeforeEach
    void createSession() {
        thread = new RecordingExecutor();
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

    /** An invalid frame doesn't stop the session: a valid frame after it still works. */
    @Test
    void invalidFrameDoesNotStopTheSession() throws Exception {
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

    /** Every frame from the hub reaches the listener method named after its type. */
    @Test
    void hubFramesReachTheListener() throws Exception {
        accept();
        String uuid = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
        hub(new S2CChatMessage("gtnh", "GTNH", 5, "Steve", uuid, "hi"));
        hub(new S2CPlayerJoin("gtnh", "GTNH", 6, "Steve", uuid, 1));
        hub(new S2CPlayerLeave("gtnh", "GTNH", 7, "Steve", uuid, 0));
        hub(new S2CPlayerList("gtnh", "GTNH", 8, Collections.<PlayerInfo>emptyList()));
        hub(new S2CServerStatus("gtnh", "GTNH", 9, ServerState.OFFLINE, "gold", "gtnh.example.net", 30,
                Collections.<PlayerInfo>emptyList()));
        hub(new S2CRunCommand("c1", "tps", "forge tps", Collections.<String>emptyList()));
        hub(new S2CSystemMessage("Restart in 5 minutes", S2CSystemMessage.Kind.RESTART));
        hub(new S2CMuteList(Collections.singletonList(uuid)));
        hub(new S2CMute(uuid, true, "discord-1"));
        sync();
        assertEquals(Arrays.asList("chatMessage 5", "playerJoin 6", "playerLeave 7", "playerList 8", "serverStatus 9",
                "runCommand", "systemMessage", "muteList", "mute"), listener.heard);
    }

    /** A reconnect tells the same hub what it last relayed here, so the hub can replay the rest. */
    @Test
    void reconnectSendsHubSessionAndLastRelayed() throws Exception {
        accept();
        hub(new S2CChatMessage("gtnh", "GTNH", 7, "Steve", "069a79f4-44e9-4726-a5be-fca90e38aaf5", "hi"));
        reconnect(0);
        C2SHandshake handshake = handshake(transport.sent.get(0));
        assertEquals(1, handshake.getHubSession());
        assertEquals(7, handshake.getLastRelayed());
    }

    /** A restarted hub numbers relayed frames from 1 again, so the old lastRelayed is forgotten. */
    @Test
    void restartedHubResetsLastRelayed() throws Exception {
        accept();
        hub(new S2CChatMessage("gtnh", "GTNH", 7, "Steve", "069a79f4-44e9-4726-a5be-fca90e38aaf5", "hi"));
        session.onClose(1001, "");
        session.onOpen();
        hub(new S2CHandshakeAccepted(Protocol.VERSION, 2, 0, Collections.<S2CServerEntry>emptyList()));
        reconnect(0);
        C2SHandshake handshake = handshake(transport.sent.get(0));
        assertEquals(2, handshake.getHubSession());
        assertEquals(0, handshake.getLastRelayed());
    }

    @Test
    void invalidFrameIsLoggedAndAnswered() throws Exception {
        accept();
        session.onFrame("{");
        sync();
        assertEquals("invalid_frame", lastProtocolError().getCode());
        assertEquals(Collections.singletonList("warn"), listener.heard);
        assertTrue(transport.closes.isEmpty());
    }

    @Test
    void threeInvalidFramesInARowClose() throws Exception {
        accept();
        for (int i = 0; i < 3; i++) {
            session.onFrame("{");
        }
        sync();
        assertEquals("protocol_error", lastProtocolError().getCode());
        assertEquals(Arrays.asList("warn", "warn", "error"), listener.heard);
        assertEquals(Collections.singletonList(Protocol.CLOSE_PROTOCOL_ERROR), transport.closes);
    }

    /** Only three in a row close: a valid frame in between starts the count again. */
    @Test
    void validFrameResetsTheInvalidCount() throws Exception {
        accept();
        session.onFrame("{");
        session.onFrame("{");
        hub(new S2CAcknowledgement(1));
        session.onFrame("{");
        sync();
        assertTrue(transport.closes.isEmpty());
    }

    @Test
    void protocolErrorFromTheHubIsLogged() throws Exception {
        accept();
        hub(new ProtocolError("invalid_frame", "missing field seq"));
        sync();
        assertEquals(Collections.singletonList("warn"), listener.heard);
        assertTrue(transport.closes.isEmpty());
    }

    /** The last frame sent, which must be a protocolError. */
    private ProtocolError lastProtocolError() throws ProtocolException {
        return assertInstanceOf(ProtocolError.class,
                PayloadCodec.C2S.decode(transport.sent.get(transport.sent.size() - 1)));
    }

    /** Hands the session a frame as if the hub sent it. */
    private void hub(Payload payload) {
        session.onFrame(PayloadCodec.encode(payload));
    }

    @Test
    void normalCloseDoesNothing() throws Exception {
        accept();
        session.onClose(Protocol.CLOSE_NORMAL, "");
        sync();
        assertEquals(Collections.emptyList(), thread.delays);
        assertEquals(0, transport.connects);
        assertEquals(Collections.emptyList(), listener.heard);
    }

    /** A bad id or a newer connection won't fix itself, so the session logs it and stays closed. */
    @Test
    void badServerIdAndReplacedStop() throws Exception {
        session.onClose(Protocol.CLOSE_BAD_SERVER_ID, "");
        session.onClose(Protocol.CLOSE_REPLACED, "");
        sync();
        assertEquals(Collections.emptyList(), thread.delays);
        assertEquals(0, transport.connects);
        assertEquals(Arrays.asList("error", "error"), listener.heard);
    }

    @Test
    void badTokenAndVersionRetryEveryMinute() throws Exception {
        session.onClose(Protocol.CLOSE_BAD_TOKEN, "");
        session.onClose(Protocol.CLOSE_BAD_VERSION, "");
        sync();
        assertEquals(Arrays.asList(60_000L, 60_000L), thread.delays);
        assertEquals(2, transport.connects);
        assertEquals(Arrays.asList("error", "error"), listener.heard);
    }

    /** Drops, a failed connect (-1) and a too big frame all back off: 1, 2, 4 s, with jitter. */
    @Test
    void otherClosesBackOff() throws Exception {
        session.onClose(1006, "");
        session.onClose(-1, "");
        session.onClose(Protocol.CLOSE_TOO_BIG, "");
        sync();
        assertBackoff(Arrays.asList(1L, 2L, 4L), thread.delays);
        assertEquals(3, transport.connects);
        assertEquals(Arrays.asList("warn", "warn", "error"), listener.heard);
    }

    @Test
    void acceptedConnectionResetsTheBackoff() throws Exception {
        session.onClose(1006, "");
        session.onClose(1006, "");
        accept();
        session.onClose(1006, "");
        sync();
        assertBackoff(Arrays.asList(1L, 2L, 1L), thread.delays);
    }

    private static void assertBackoff(List<Long> seconds, List<Long> millis) {
        assertEquals(seconds.size(), millis.size(), "delays " + millis);
        for (int i = 0; i < seconds.size(); i++) {
            long base = seconds.get(i) * 1000;
            assertTrue(millis.get(i) >= base * 8 / 10 && millis.get(i) <= base * 12 / 10, "delays " + millis);
        }
    }

    @Test
    void heartbeatOnlyWhileAccepted() throws Exception {
        session.onOpen();
        session.heartbeat();
        accept();
        session.heartbeat();
        sync();
        List<String> types = new ArrayList<>();
        for (String frame : transport.sent) {
            types.add(PayloadCodec.C2S.decode(frame).type());
        }
        assertEquals(Arrays.asList("handshake", "handshake", "playerList", "heartbeat"), types);
    }

    /** stop returns as soon as the hub acknowledges the stopped status, then closes with 1000. */
    @Test
    void stopWaitsForTheAck() throws Exception {
        accept();
        Thread stopping = new Thread(() -> session.stop(ServerState.STOPPED));
        stopping.start();
        // playerList took seq 1, so stopped is 2
        for (int i = 0; i < 100 && !eventsSent().contains("serverStatus 2"); i++) {
            Thread.sleep(10);
        }
        assertTrue(stopping.isAlive(), "stop returned before the ack");
        hub(new S2CAcknowledgement(2));
        stopping.join(1000);
        assertFalse(stopping.isAlive(), "stop didn't return after the ack");
        sync();
        assertEquals(Collections.singletonList(Protocol.CLOSE_NORMAL), transport.closes);
    }

    /** With no hub to answer, stop gives up after 3 s, closes, and nothing reconnects afterwards. */
    @Test
    void stopGivesUpAfterThreeSecondsAndNeverReconnects() throws Exception {
        long start = System.nanoTime();
        session.stop(ServerState.CRASHED);
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(millis >= 2900 && millis < 5000, "stop took " + millis + " ms");
        session.onClose(1006, "");
        sync();
        assertEquals(Collections.singletonList(Protocol.CLOSE_NORMAL), transport.closes);
        assertEquals(0, transport.connects);
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

    /**
     * Drops the connection, forgets what was sent so far, and reconnects to a hub (session 1) that has up to lastSeq.
     * Waits for the session, so transport.sent then holds the new handshake first.
     */
    private void reconnect(long lastSeq) throws Exception {
        session.onClose(1006, "");
        sync();
        transport.sent.clear();
        session.onOpen();
        session.onFrame(PayloadCodec.encode(new S2CHandshakeAccepted(Protocol.VERSION, 1, lastSeq,
                Collections.<S2CServerEntry>emptyList())));
        sync();
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

    /** Waits until every task already posted to the session's thread, and any task those posted, has run. */
    private void sync() throws InterruptedException, ExecutionException {
        // Twice, so a task that a posted task posted in turn (a reconnect after a close) has run too
        for (int i = 0; i < 2; i++) {
            thread.submit(() -> {
            }).get();
        }
    }

    private static C2SHandshake handshake(String frame) throws ProtocolException {
        return assertInstanceOf(C2SHandshake.class, PayloadCodec.C2S.decode(frame));
    }

    /**
     * The session's thread for tests. Records the delay of every task scheduled with one, then runs it at once, so
     * reconnect tests don't wait seconds.
     */
    private static final class RecordingExecutor extends ScheduledThreadPoolExecutor {
        final List<Long> delays = Collections.synchronizedList(new ArrayList<>());

        RecordingExecutor() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            // execute and submit come through here too, with no delay; only real delays are recorded
            if (delay > 0) {
                delays.add(unit.toMillis(delay));
            }
            return super.schedule(command, 0, unit);
        }
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
        /** Every other frame handed over, as its type, plus " seq" for a relayed frame; "warn" or "error" for a log. */
        final List<String> heard = new ArrayList<>();

        @Override
        public void servers(List<S2CServerEntry> servers) {
            this.servers = servers;
        }

        @Override
        public void chatMessage(S2CChatMessage message) {
            relayed(message);
        }

        @Override
        public void playerJoin(S2CPlayerJoin message) {
            relayed(message);
        }

        @Override
        public void playerLeave(S2CPlayerLeave message) {
            relayed(message);
        }

        @Override
        public void playerList(S2CPlayerList message) {
            relayed(message);
        }

        @Override
        public void serverStatus(S2CServerStatus message) {
            relayed(message);
        }

        @Override
        public void runCommand(S2CRunCommand message) {
            heard.add(message.type());
        }

        @Override
        public void systemMessage(S2CSystemMessage message) {
            heard.add(message.type());
        }

        @Override
        public void muteList(S2CMuteList message) {
            heard.add(message.type());
        }

        @Override
        public void mute(S2CMute message) {
            heard.add(message.type());
        }

        @Override
        public void warn(String message) {
            heard.add("warn");
        }

        @Override
        public void error(String message) {
            heard.add("error");
        }

        private void relayed(S2CRelayed message) {
            heard.add(message.type() + " " + message.getSeq());
        }
    }
}
