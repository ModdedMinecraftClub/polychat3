package club.moddedminecraft.polychat3.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import club.moddedminecraft.polychat3.core.client.MessageListener;
import club.moddedminecraft.polychat3.core.payloads.ServerState;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CChatMessage;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CMute;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CMuteList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CRunCommand;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerEntry;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerStatus;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CSystemMessage;

import computer.heather.goagnostic.api.baseclasses.GoAgnosticMod;
import computer.heather.goagnostic.api.command.CommandResult;

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
    /** The mute list the hub sends, shared with PolychatClient.onChat. Written here, on the session thread. */
    private final MuteList mutes;
    /** Who sees Polychat lines. Used inside the tasks show posts, so on the game thread only. */
    private final Audience audience;

    public PolychatListener(GoAgnosticMod.Logger logger, GameThread gameThread, MuteList mutes, Audience audience) {
        this.logger = logger;
        this.gameThread = gameThread;
        this.mutes = mutes;
        this.audience = audience;
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

    @Override
    public void muteList(S2CMuteList message) {
        mutes.replace(message.getMuted());
    }

    @Override
    public void mute(S2CMute message) {
        mutes.set(message.getTarget(), message.isMuted());
    }

    @Override
    public void runCommand(S2CRunCommand message) {
        String command = message.commandLine(PolychatClient.CONFIG.getCommand(message.getName()));
        logger.info("Running " + command + " for the hub");
        gameThread.post(() -> {
            try {
                CommandResult result = PolychatClient.instance().getServer().executeCommand(command);
                PolychatClient.instance().session().commandResult(message.getId(),
                        Boolean.TRUE.equals(result.getSuccess()), result.getMessage());
            } catch (RuntimeException e) {
                // Still answer, so the hub isn't left waiting for this id
                logger.warn("Command " + command + " for the hub failed: " + e);
                PolychatClient.instance().session().commandResult(message.getId(), false, e.toString());
            }
        });

    }

    /** The colour for a server id; blue for one the hub never listed, such as Discord's, as polychat2 showed it. */
    private String colour(String server) {
        return colours.getOrDefault(server, "blue");
    }

    /**
     * Shows a line to every player who hasn't silenced Polychat, on the game thread at the next tick: only that
     * thread may touch the game.
     */
    private void show(String line) {
        gameThread.post(() -> audience.show(line));
    }

}
