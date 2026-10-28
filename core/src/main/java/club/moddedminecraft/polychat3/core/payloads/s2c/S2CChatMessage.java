package club.moddedminecraft.polychat3.core.payloads.s2c;

/**
 * A chat line from another server, or from Discord.
 */
public final class S2CChatMessage extends S2CRelayed {
    public static final String TYPE = "chatMessage";

    private final String player;
    private final String senderId;
    private final String message;

    public S2CChatMessage(String server, String serverName, long seq, String player, String senderId, String message) {
        super(server, serverName, seq);
        this.player = player;
        this.senderId = senderId;
        this.message = message;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return name of whoever sent it.
     */
    public String getPlayer() {
        return player;
    }

    /**
     * @return minecraft UUID for game chat, Discord user id for Discord chat.
     */
    public String getSenderId() {
        return senderId;
    }

    /**
     * @return plain text, without formatting codes.
     */
    public String getMessage() {
        return message;
    }
}
