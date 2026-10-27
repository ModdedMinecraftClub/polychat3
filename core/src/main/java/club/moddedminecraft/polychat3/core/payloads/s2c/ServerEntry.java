package club.moddedminecraft.polychat3.core.payloads.s2c;

import java.util.List;

import club.moddedminecraft.polychat3.core.payloads.PlayerInfo;
import club.moddedminecraft.polychat3.core.payloads.ServerState;

/**
 * One server in the hub's roster, as sent in {@code handshakeAccepted.servers}.
 */
public final class ServerEntry {
    private final String id;
    private final String name;
    private final String colour;
    private final String address;
    private final int maxPlayers;
    private final ServerState state;
    private final List<PlayerInfo> players;

    public ServerEntry(String id, String name, String colour, String address, int maxPlayers,
            ServerState state, List<PlayerInfo> players) {
        this.id = id;
        this.name = name;
        this.colour = colour;
        this.address = address;
        this.maxPlayers = maxPlayers;
        this.state = state;
        this.players = players;
    }

    /**
     * @return server id.
     */
    public String getId() {
        return id;
    }

    /**
     * @return display name.
     */
    public String getName() {
        return name;
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
     * @return current state, including {@code OFFLINE} and {@code UNRESPONSIVE}.
     */
    public ServerState getState() {
        return state;
    }

    /**
     * @return every player online; empty unless the server is running.
     */
    public List<PlayerInfo> getPlayers() {
        return players;
    }
}
