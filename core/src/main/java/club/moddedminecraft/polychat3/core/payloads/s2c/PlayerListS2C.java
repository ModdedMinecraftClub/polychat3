package club.moddedminecraft.polychat3.core.payloads.s2c;

import java.util.List;

import club.moddedminecraft.polychat3.core.payloads.PlayerInfo;

/**
 * The full list of players on another server.
 */
public final class PlayerListS2C extends Relayed {
    public static final String TYPE = "playerList";

    private final List<PlayerInfo> players;

    public PlayerListS2C(String server, String serverName, long seq, List<PlayerInfo> players) {
        super(server, serverName, seq);
        this.players = players;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return every player online there.
     */
    public List<PlayerInfo> getPlayers() {
        return players;
    }
}
