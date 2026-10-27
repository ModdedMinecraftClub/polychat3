package club.moddedminecraft.polychat3.core.payloads.s2c;

/**
 * A chat line from another server, or from Discord.
 */
public final class ChatMessage extends Relayed {
    public static final String TYPE = "chatMessage";

    private final String player;
    private final String senderId;
    private final String message;

    public ChatMessage(String server, String serverName, String player, String senderId, String message) {
        super(server, serverName);
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
