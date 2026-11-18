package club.moddedminecraft.polychat3.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import club.moddedminecraft.polychat3.core.client.MessageListener;
import club.moddedminecraft.polychat3.core.payloads.ServerState;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CChatMessage;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerEntry;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerStatus;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CSystemMessage;

import computer.heather.goagnostic.api.baseclasses.GoAgnosticMod;
import computer.heather.goagnostic.api.util.chat.ChatManager;

/**
 * What the hub sends, handed on to the game, and the session's warnings and errors, sent to the mod's log. Called on
 * the session's thread, so anything that touches the game must be passed to the game thread.
 */
final class PolychatListener implements MessageListener {
    private final GoAgnosticMod.Logger logger;
    private final GameThread gameThread;
    /** Each other server's colour, by id, from handshakeAccepted and every serverStatus. Session thread only. */
    private final Map<String, String> colours = new HashMap<>();
    /** Each other server's last state, by id, so a reconnect that changes nothing shows no line. Session thread only. */
    private final Map<String, ServerState> states = new HashMap<>();

    public PolychatListener(GoAgnosticMod.Logger logger, GameThread gameThread) {
        this.logger = logger;
        this.gameThread = gameThread;
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }

    @Override
    public void servers(List<S2CServerEntry> servers) {
        for (S2CServerEntry server : servers) {
            colours.put(server.getId(), server.getColour());
            states.put(server.getId(), server.getState());
        }
    }

    @Override
    public void chatMessage(S2CChatMessage message) {
        show(Formats.chat(colour(message.getServer()), message.getServer(), message.getPlayer(), message.getMessage()));
    }

    @Override
    public void playerJoin(S2CPlayerJoin message) {
        show(Formats.joined(colour(message.getServer()), message.getServer(), message.getPlayer()));
    }

    @Override
    public void playerLeave(S2CPlayerLeave message) {
        show(Formats.left(colour(message.getServer()), message.getServer(), message.getPlayer()));
    }

    @Override
    public void serverStatus(S2CServerStatus message) {
        colours.put(message.getServer(), message.getColour());
        ServerState state = message.getState();
        // put returns the state stored before, or null the first time this server is seen
        if (!state.equals(states.put(message.getServer(), state))) {
            show(Formats.status(colour(message.getServer()), message.getServer(), message.getServerName(), state));
        }
    }

    @Override
    public void systemMessage(S2CSystemMessage message) {
        show(message.getMessage());
    }

    /** The colour for a server id; blue for one the hub never listed, such as Discord's, as polychat2 showed it. */
    private String colour(String server) {
        return colours.getOrDefault(server, "blue");
    }

    /** Shows a line to every player, on the game thread at the next tick: only that thread may touch the game. */
    private void show(String line) {
        gameThread.post(() -> ChatManager.sendSystemMessage(line));
    }
}
