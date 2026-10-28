package club.moddedminecraft.polychat3.server.hub;

import club.moddedminecraft.polychat3.core.payloads.c2s.C2SChatMessage;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SEvent;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerList;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SServerStatus;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CChatMessage;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CRelayed;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerEntry;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerStatus;

/**
 * Turns a client's event into the frame the other game servers get, numbered with the hub's relay {@code seq}, as
 * in PROTOCOL.md "Replay to the client". Only used on the hub thread.
 */
final class Relay {
    /** The relay {@code seq} of the last frame built, 0 before the first. One number for the whole hub. */
    private long lastRelayed;

    /**
     * @param origin the roster entry of the server the event came from, already updated with this event. Gives
     * the relayed frame its {@code server} and {@code serverName}, and {@code serverStatus} everything else.
     * @return the frame to relay, with the next relay {@code seq}, or null if this event type isn't relayed. The
     * {@code seq} only advances when a frame is returned.
     */
    S2CRelayed relayed(S2CServerEntry origin, C2SEvent event) {
        String server = origin.getId();
        String serverName = origin.getName();

        if (event instanceof C2SChatMessage) {
            C2SChatMessage chat = (C2SChatMessage) event;
            return new S2CChatMessage(server, serverName, ++lastRelayed, chat.getPlayer(),
                    chat.getSenderId(), chat.getMessage());
        }

        if (event instanceof C2SPlayerJoin) {
            C2SPlayerJoin join = (C2SPlayerJoin) event;
            return new S2CPlayerJoin(server, serverName, ++lastRelayed, join.getPlayer(), join.getUuid(),
                    join.getOnline());
        }

        if (event instanceof C2SPlayerLeave) {
            C2SPlayerLeave leave = (C2SPlayerLeave) event;
            return new S2CPlayerLeave(server, serverName, ++lastRelayed, leave.getPlayer(), leave.getUuid(),
                    leave.getOnline());
        }

        if (event instanceof C2SPlayerList) {
            C2SPlayerList list = (C2SPlayerList) event;
            return new S2CPlayerList(server, serverName, ++lastRelayed, list.getPlayers());
        }

        if (event instanceof C2SServerStatus) {
            return new S2CServerStatus(server, serverName, ++lastRelayed, origin.getState(), origin.getColour(),
                    origin.getAddress(), origin.getMaxPlayers(), origin.getPlayers());
        }

        return null;
    }
}