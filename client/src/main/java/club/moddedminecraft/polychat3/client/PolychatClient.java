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
import computer.heather.goagnostic.api.util.player.Player;

@Mod(modName = "Polychat3", modid = "polychat3", targets = { Target.NEO_1_21, Target.FORGE_1_12, Target.FORGE_1_7_10 }, 
    version = "1", group = "club moddedminecraft", authors = { "Heather White", "Edward Pratt" }, 
    description = "The third iteration of Polychat!", license = "BSD-3-Clause")
public class PolychatClient extends GoAgnosticMod{

    public static PolychatClientConfig CONFIG;
    /** The connection to the hub. */
    private ClientSession session;
    /** Who is online, by UUID. The game thread changes it; the session's thread copies it for playerList. */
    private final Map<String, PlayerInfo> players = new ConcurrentHashMap<>();
    /** The hub's mute list. PolychatListener fills it on the session thread; onChat reads it on the game thread. */
    private final MuteList mutes = new MuteList();
    /** Who sees Polychat lines, shared by PolychatListener and /polychat silence. Game thread only. */
    private final Audience audience = new Audience();
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
    /** PROTOCOL.md: the full player list every 5 minutes, so the hub's copy can't drift from the game's. */
    private static final long PLAYER_LIST_NANOS = TimeUnit.MINUTES.toNanos(5);
    /** System.nanoTime() of the last player list; the session sent one with the handshake. Game thread only. */
    private long lastPlayerList = System.nanoTime();
    private final GameThread gameThread;

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

        gameThread = new GameThread(logger);
        session = new ClientSession(CONFIG.getServerId(), CONFIG.getName(), CONFIG.getToken(), colour,
                CONFIG.getAddress(), CONFIG.getMaxPlayers(), transport,
                new PolychatListener(logger, gameThread, mutes, audience),
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
        gameThread.runAll();
        long now = System.nanoTime();
        if (now - lastHeartbeat > HEARTBEAT_NANOS) {
            lastHeartbeat = now;
            session.heartbeat();
        }
        if (now - lastPlayerList > PLAYER_LIST_NANOS) {
            lastPlayerList = now;
            session.playerList();
        }
    }

    @Override
    public void playerJoin(Player player) {
        players.put(player.getUUID(), new PlayerInfo(player.getName(), player.getUUID()));
        session.playerJoin(player.getName(), player.getUUID(), players.size());
    }

    @Override
    public void playerLeave(Player player) {
        players.remove(player.getUUID());
        session.playerLeave(player.getName(), player.getUUID(), players.size());
    }

    /** A player on this server chatted. Go!Agnostic's ChatManager calls it on the game thread. */
    public void onChat(ChatMessage message) {
        Player sender = message.getSender();
        if (mutes.isMuted(sender.getUUID())) {
            message.blockMessage();
            sender.sendMessage("You are muted.");
            return;
        }
        session.chatMessage(sender.getName(), sender.getUUID(), message.getText());
    }

    /** The one PolychatClient, for the command classes, which are static and have no instance of their own. */
    static PolychatClient instance() {
        return (PolychatClient) getInstance();
    }

    /** The connection to the hub, so commands can send to it. */
    ClientSession session() {
        return session;
    }

    /** The mute list, so commands can check whether a player is already muted. */
    MuteList mutes() {
        return mutes;
    }

    Audience audience() {
        return audience;
    }

    
}
