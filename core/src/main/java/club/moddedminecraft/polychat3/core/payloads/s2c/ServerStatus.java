package club.moddedminecraft.polychat3.core.payloads.s2c;

import java.util.List;

import club.moddedminecraft.polychat3.core.payloads.PlayerInfo;
import club.moddedminecraft.polychat3.core.payloads.ServerState;

/**
 * Another server's state changed. Carries that server's whole roster entry.
 */
public final class ServerStatus extends Relayed {
    public static final String TYPE = "serverStatus";

    private final ServerState state;
    private final String colour;
    private final String address;
    private final int maxPlayers;
    private final List<PlayerInfo> players;

    public ServerStatus(String server, String serverName, ServerState state, String colour, String address,
            int maxPlayers, List<PlayerInfo> players) {
        super(server, serverName);
        this.state = state;
        this.colour = colour;
        this.address = address;
        this.maxPlayers = maxPlayers;
        this.players = players;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return new state, including {@code OFFLINE} and {@code UNRESPONSIVE}.
     */
    public ServerState getState() {
        return state;
    }

    /**
     * @return one of the 16 Minecraft colour names.
     */
    public String getColour() {
        return colour;
    }

    /**
     * @return the address players connect to.
     */
    public String getAddress() {
        return address;
    }

    /**
     * @return player limit.
     */
    public int getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * @return every player online there; empty unless it is running.
     */
    public List<PlayerInfo> getPlayers() {
        return players;
    }
}
