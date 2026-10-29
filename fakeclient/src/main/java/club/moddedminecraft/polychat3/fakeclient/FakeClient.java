package club.moddedminecraft.polychat3.fakeclient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import club.moddedminecraft.polychat3.core.client.ClientSession;
import club.moddedminecraft.polychat3.core.client.MessageListener;
import club.moddedminecraft.polychat3.core.payloads.PlayerInfo;
import club.moddedminecraft.polychat3.core.payloads.ServerState;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CChatMessage;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerEntry;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerStatus;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CSystemMessage;

/**
 * A game server without the game, for testing against a hub: prints what the hub sends, sends each typed line as
 * chat, and stops cleanly on {@code /stop} or end of input. Arguments {@code --url}, {@code --id}, {@code --name}; the
 * token comes from {@code POLYCHAT_TOKEN}.
 */
public final class FakeClient {
    // The fake player every typed line is sent as
    private static final String PLAYER = "Tester";
    private static final String PLAYER_UUID = "00000000-0000-0000-0000-000000000001";

    public static void main(String[] args) throws Exception {
        // --url ws://localhost:5005 --id fa --name "Fake A"
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i], args[i + 1]);
        }
        String url = options.getOrDefault("--url", "ws://localhost:5005");
        String id = options.getOrDefault("--id", "fake");
        String name = options.getOrDefault("--name", id);
        // From the environment, so it never shows in shell history or ps
        String token = System.getenv("POLYCHAT_TOKEN");
        if (token == null || token.length() == 0) {
            System.err.println("Set POLYCHAT_TOKEN");
            System.exit(1);
        }

        JavaWebSocketTransport transport = new JavaWebSocketTransport(new URI(url));
        MessageListener listener = new MessageListener() {
            @Override
            public void chatMessage(S2CChatMessage message) {
                System.out.println("[" + message.getServerName() + "] " + message.getPlayer() + ": "
                        + message.getMessage());
            }

            @Override
            public void servers(List<S2CServerEntry> servers) {
                for (S2CServerEntry server : servers) {
                    System.out.println("* " + server.getId() + " (" + server.getName() + ") " + server.getState() + ", "
                            + server.getPlayers().size() + " online");
                }
            }

            @Override
            public void serverStatus(S2CServerStatus message) {
                System.out.println("* " + message.getServerName() + " is now " + message.getState());
            }

            @Override
            public void playerJoin(S2CPlayerJoin message) {
                System.out.println("* " + message.getPlayer() + " joined " + message.getServerName());
            }

            @Override
            public void playerLeave(S2CPlayerLeave message) {
                System.out.println("* " + message.getPlayer() + " left " + message.getServerName());
            }

            @Override
            public void systemMessage(S2CSystemMessage message) {
                System.out.println("! " + message.getMessage());
            }
        };
        // Who is online, as a game would know it; the session asks for a copy after every handshakeAccepted
        List<PlayerInfo> online = new CopyOnWriteArrayList<>();
        ClientSession session = new ClientSession(id, name, token, "gray", "localhost", 20, transport, listener,
                () -> new ArrayList<>(online));
        transport.setSession(session);

        // The server starts and Tester joins before the first connection: the events wait in the session's buffer
        // and go out after handshakeAccepted, followed by a player list that already has Tester
        session.serverStatus(ServerState.STARTED);
        online.add(new PlayerInfo(PLAYER, PLAYER_UUID));
        session.playerJoin(PLAYER, PLAYER_UUID, online.size());
        session.start();

        // PROTOCOL.md "Shutting down": the client stops from a shutdown hook, which runs on System.exit, Ctrl+C and
        // SIGTERM alike. Tester leaves first, as players do when a real server stops
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            online.clear();
            session.playerLeave(PLAYER, PLAYER_UUID, 0);
            session.stop(ServerState.STOPPED);
        }, "polychat-stop"));

        // The heartbeat a game's tick would send, every 15 s
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        timer.scheduleAtFixedRate(session::heartbeat, 15, 15, TimeUnit.SECONDS);

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = in.readLine()) != null && !line.equals("/stop")) {
            session.chatMessage(PLAYER, PLAYER_UUID, line);
        }
        // Runs the shutdown hook, which stops the session
        System.exit(0);
    }
}
