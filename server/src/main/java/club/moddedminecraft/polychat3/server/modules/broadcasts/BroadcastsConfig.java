package club.moddedminecraft.polychat3.server.modules.broadcasts;

import java.util.List;

/** config/broadcasts.jsonc: the messages to broadcast in turn, and how often. */
public class BroadcastsConfig {
    //These match the exact names in the jsonc config
    private int interval_minutes = 7;
    private List<String> messages;

    /** Minutes between two broadcasts; also how long until the first. */
    public int getIntervalMinutes() {
        return interval_minutes;
    }

    /** The messages, sent one per interval in this order, then from the start again. */
    public List<String> getMessages() {
        return messages;
    }

    /**
     * @throws IllegalArgumentException naming the first setting that is missing or invalid
     */
    public void validate() {
        if (interval_minutes < 1) {
            throw new IllegalArgumentException("interval_minutes must be at least 1, is " + interval_minutes);
        } else if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must list at least one message");
        }
    }
}
