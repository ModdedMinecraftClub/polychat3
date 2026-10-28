package club.moddedminecraft.polychat3.core.payloads.s2c;

/**
 * A player joined another server.
 */
public final class S2CPlayerJoin extends S2CRelayed {
    public static final String TYPE = "playerJoin";

    private final String player;
    private final String uuid;
    private final int online;

    public S2CPlayerJoin(String server, String serverName, long seq, String player, String uuid, int online) {
        super(server, serverName, seq);
        this.player = player;
        this.uuid = uuid;
        this.online = online;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return player name.
     */
    public String getPlayer() {
        return player;
    }

    /**
     * @return the player's Minecraft UUID, dashed.
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * @return number of players online there after the player joined.
     */
    public int getOnline() {
        return online;
    }
}
