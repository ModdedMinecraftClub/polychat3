package club.moddedminecraft.polychat3.client;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import club.moddedminecraft.polychat3.client.config.PolychatClientConfig;
import club.moddedminecraft.polychat3.core.client.ClientSession;
import club.moddedminecraft.polychat3.core.config.ConfigLoader;
import club.moddedminecraft.polychat3.core.payloads.PlayerInfo;
import club.moddedminecraft.polychat3.core.payloads.ServerState;

import computer.heather.goagnostic.api.annotations.Mod;
import computer.heather.goagnostic.api.annotations.Mod.Target;
import computer.heather.goagnostic.api.baseclasses.GoAgnosticMod;
import computer.heather.goagnostic.api.util.chat.ChatManager;
import computer.heather.goagnostic.api.util.chat.ChatMessage;

@Mod(modName = "Polychat3", modid = "polychat3", targets = { Target.NEO_1_21, Target.FORGE_1_12, Target.FORGE_1_7_10 }, 
    version = "1", group = "club moddedminecraft", authors = { "Heather White", "Edward Pratt" }, 
    description = "The third iteration of Polychat!", license = "BSD-3-Clause")
public class PolychatClient extends GoAgnosticMod{

    public static PolychatClientConfig CONFIG;
    /** The connection to the hub. */
    private ClientSession session;
    /** Who is online, by UUID. The game thread changes it; the session's thread copies it for playerList. */
    private final Map<String, PlayerInfo> players = new ConcurrentHashMap<>();
    /**
     * Set by onServerStopping, so the shutdown hook can tell a stop from a crash, as polychat2 did. Volatile because
     * the hook's thread reads what the game thread wrote.
     */
    private volatile boolean cleanStop;
    /** PROTOCOL.md "Keepalive": a heartbeat every 15 s from the game's tick, so it stops if the game thread hangs. */
    private static final long HEARTBEAT_NANOS = TimeUnit.SECONDS.toNanos(15);
    /**
     * System.nanoTime() of the last heartbeat. Starts at construction, not 0, since nanoTime values only mean
     * something compared with each other. Game thread only.
     */
    private long lastHeartbeat = System.nanoTime();

    public PolychatClient(String modName, String modId, Logger logger) throws Exception {
        super(modName, modId, logger);

        //Init config

        CONFIG = ConfigLoader.load(Paths.get("config/polychat-client.jsonc"), PolychatClientConfig.class, "/defaults/polychat-client.jsonc");
        CONFIG.validate();

        // The hub's address, e.g. ws://localhost:5005
        URI hub = URI.create("ws://" + CONFIG.getServerAddress() + ":" + CONFIG.getServerPort());
        JavaWebSocketTransport transport = new JavaWebSocketTransport(hub, logger);

        // The wire wants the Minecraft colour name, dark_blue, not the enum name, DARK_BLUE
        String colour = CONFIG.getColour().name().toLowerCase(Locale.ROOT);
        session = new ClientSession(CONFIG.getServerId(), CONFIG.getName(), CONFIG.getToken(), colour,
                CONFIG.getAddress(), CONFIG.getMaxPlayers(), transport, new PolychatListener(logger),
                () -> new ArrayList<>(players.values()));

        // The transport and the session each need the other, so the session goes in after both exist
        transport.setSession(session);
        session.start();
        // PROTOCOL.md "Shutting down": the JVM runs this after the server stops, or after a crash, which never
        // called onServerStopping
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> session.stop(cleanStop ? ServerState.STOPPED : ServerState.CRASHED), "polychat-stop"));

        //Listen to chat
        ChatManager.setChatListener(this::onChat);



    }

    @Override
    public void onServerStarted() {
        session.serverStatus(ServerState.STARTED);
    }

    @Override
    public void onServerStopping() {
        cleanStop = true;
        session.serverStatus(ServerState.STOPPING);
    }

    @Override
    public void serverTick() {
        long now = System.nanoTime();
        if (now - lastHeartbeat > HEARTBEAT_NANOS) {
            lastHeartbeat = now;
            session.heartbeat();
        }
    }

    public void onChat(ChatMessage message) {

    }


    
}
