package club.moddedminecraft.polychat3.core.payloads.c2s;

import java.util.List;

import club.moddedminecraft.polychat3.core.payloads.PlayerInfo;

/**
 * The full list of players on this server. Sent after every {@code handshakeAccepted},
 * and every 5 minutes.
 */
public final class PlayerList extends Event {
    public static final String TYPE = "playerList";

    private final List<PlayerInfo> players;

    public PlayerList(long seq, List<PlayerInfo> players) {
        super(seq);
        this.players = players;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return every player online.
     */
    public List<PlayerInfo> getPlayers() {
        return players;
    }
}
