package club.moddedminecraft.polychat3.core.payloads;

/**
 * A player on a server.
 */
public final class PlayerInfo {
    private final String name;
    private final String uuid;

    public PlayerInfo(String name, String uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    /**
     * @return player name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return minecraft UUID, dashed.
     */
    public String getUuid() {
        return uuid;
    }
}
