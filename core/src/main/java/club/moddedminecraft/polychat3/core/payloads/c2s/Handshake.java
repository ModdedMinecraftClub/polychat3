package club.moddedminecraft.polychat3.core.payloads.c2s;

import club.moddedminecraft.polychat3.core.payloads.Payload;
import club.moddedminecraft.polychat3.core.payloads.ServerState;

/**
 * The first frame a client sends. The hub answers {@code handshakeAccepted} or closes
 * the connection.
 */
public final class Handshake extends Payload {
    public static final String TYPE = "handshake";

    private final int version;
    private final String server;
    private final String name;
    private final String token;
    private final long session;
    private final ServerState state;
    private final String colour;
    private final String address;
    private final int maxPlayers;

    public Handshake(int version, String server, String name, String token, long session, ServerState state,
            String colour, String address, int maxPlayers) {
        this.version = version;
        this.server = server;
        this.name = name;
        this.token = token;
        this.session = session;
        this.state = state;
        this.colour = colour;
        this.address = address;
        this.maxPlayers = maxPlayers;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return protocol version; must equal {@code Protocol.VERSION}.
     */
    public int getVersion() {
        return version;
    }

    /**
     * @return this server's id: 1 to 16 lowercase letters, digits, {@code _} or {@code -}.
     */
    public String getServer() {
        return server;
    }

    /**
     * @return display name, for example {@code All the Mods 5}.
     */
    public String getName() {
        return name;
    }

    /**
     * @return the token from the hub config, shared by every server.
     */
    public String getToken() {
        return token;
    }

    /**
     * @return process start time in ms. A new value tells the hub the server restarted, so {@code seq}
     * starts again from 1; the same value means a reconnect.
     */
    public long getSession() {
        return session;
    }

    /**
     * @return {@link ServerState#STARTING} or {@link ServerState#STARTED}.
     */
    public ServerState getState() {
        return state;
    }

    /**
     * @return one of the 16 Minecraft colour names, for example {@code yellow}.
     */
    public String getColour() {
        return colour;
    }

    /**
     * @return the address players connect to, shown by {@code /online}.
     */
    public String getAddress() {
        return address;
    }

    /**
     * @return player limit, shown by {@code /online}.
     */
    public int getMaxPlayers() {
        return maxPlayers;
    }
}
