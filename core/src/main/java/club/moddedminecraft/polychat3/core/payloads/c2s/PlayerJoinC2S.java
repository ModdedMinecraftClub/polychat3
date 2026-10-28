package club.moddedminecraft.polychat3.core.payloads.c2s;

/**
 * A player joined this server.
 */
public final class PlayerJoinC2S extends Event {
    public static final String TYPE = "playerJoin";

    private final String player;
    private final String uuid;
    private final int online;

    public PlayerJoinC2S(long seq, String player, String uuid, int online) {
        super(seq);
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
     * @return number of players online after the player joined.
     */
    public int getOnline() {
        return online;
    }
}
