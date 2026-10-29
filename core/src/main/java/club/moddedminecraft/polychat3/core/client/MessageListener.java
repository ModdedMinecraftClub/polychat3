package club.moddedminecraft.polychat3.core.client;

import java.util.List;

import club.moddedminecraft.polychat3.core.payloads.s2c.S2CChatMessage;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CMute;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CMuteList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CRunCommand;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerEntry;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerStatus;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CSystemMessage;

/**
 * What the mod gives {@link ClientSession} to receive the hub's frames, one method per type, as in PROTOCOL.md
 * "Messages, hub to client". Every method does nothing by default, so the mod overrides only what it needs. Called
 * on the session's thread; moving onto the game thread is the mod's job.
 */
public interface MessageListener {

    /** After every {@code handshakeAccepted}: every other game server the hub knows and its current state. */
    default void servers(List<S2CServerEntry> servers) {
    }

    /** A chat line from another server or a module. */
    default void chatMessage(S2CChatMessage message) {
    }

    /** A player joined another server. */
    default void playerJoin(S2CPlayerJoin message) {
    }

    /** A player left another server. */
    default void playerLeave(S2CPlayerLeave message) {
    }

    /** Every player on another server, sent after it connects. */
    default void playerList(S2CPlayerList message) {
    }

    /** Another server's state changed, including {@code offline} set by the hub. */
    default void serverStatus(S2CServerStatus message) {
    }

    /** Run a console command and answer with {@code commandResult}. */
    default void runCommand(S2CRunCommand message) {
    }

    /** A message from the hub for this server's players. */
    default void systemMessage(S2CSystemMessage message) {
    }

    /** Every muted player, sent after {@code handshakeAccepted}. Replaces the mod's copy. */
    default void muteList(S2CMuteList message) {
    }

    /** One player was muted or unmuted. */
    default void mute(S2CMute message) {
    }

    /** Something went wrong but the session carries on. Prints to stderr unless the mod sends it to its logger. */
    default void warn(String message) {
        System.err.println("[polychat] " + message);
    }

    /** Something the server's admin must fix, or that closed the connection. Prints to stderr by default. */
    default void error(String error) {
        System.err.println("[polychat] ERROR " + error);
    }
}
