package club.moddedminecraft.polychat3.core.payloads.s2c;

import java.util.List;

import club.moddedminecraft.polychat3.core.payloads.Payload;

/**
 * Every muted player, sent right after {@code handshakeAccepted}. Replaces the client's copy of the
 * mute list.
 */
public final class MuteList extends Payload {
    public static final String TYPE = "muteList";

    private final List<String> muted;

    public MuteList(List<String> muted) {
        this.muted = muted;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return Minecraft UUIDs of every muted player, dashed.
     */
    public List<String> getMuted() {
        return muted;
    }
}
