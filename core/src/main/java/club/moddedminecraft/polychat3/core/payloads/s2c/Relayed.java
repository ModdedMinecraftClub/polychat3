package club.moddedminecraft.polychat3.core.payloads.s2c;

import club.moddedminecraft.polychat3.core.payloads.Payload;

/**
 * An event the hub forwards from one server, or from a module such as Discord, to the others.
 */
public abstract class Relayed extends Payload {
    private final String server;
    private final String serverName;

    protected Relayed(String server, String serverName) {
        this.server = server;
        this.serverName = serverName;
    }

    /**
     * @return id of the server it came from, or the id a module claimed, such as {@code discord}.
     */
    public String getServer() {
        return server;
    }

    /**
     * @return display name of the server it came from.
     */
    public String getServerName() {
        return serverName;
    }
}
