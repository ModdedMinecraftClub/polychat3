package club.moddedminecraft.polychat3.server.hub;

import club.moddedminecraft.polychat3.core.payloads.c2s.C2SChatMessage;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SEvent;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CChatMessage;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CRelayed;

/**
 * Turns a client's event into the frame the other game servers get, numbered with the hub's relay {@code seq}, as
 * in PROTOCOL.md "Replay to the client". Only used on the hub thread.
 */
final class Relay {
    /** The relay {@code seq} of the last frame built, 0 before the first. One number for the whole hub. */
    private long lastRelayed;

    /**
     * @param server the origin's game server id.
     * @param serverName the origin's display name.
     * @return the frame to relay, with the next relay {@code seq}, or null if this event type isn't relayed. The
     * {@code seq} only advances when a frame is returned.
     */
    S2CRelayed relayed(String server, String serverName, C2SEvent event) {
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

        return null;
    }
}