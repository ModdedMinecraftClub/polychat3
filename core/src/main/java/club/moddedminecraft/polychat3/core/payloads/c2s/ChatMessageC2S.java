package club.moddedminecraft.polychat3.core.payloads.c2s;

/**
 * A chat line typed by a player on this server.
 */
public final class ChatMessageC2S extends Event {
    public static final String TYPE = "chatMessage";

    private final String player;
    private final String senderId;
    private final String message;

    public ChatMessageC2S(long seq, String player, String senderId, String message) {
        super(seq);
        this.player = player;
        this.senderId = senderId;
        this.message = message;
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
