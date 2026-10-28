package club.moddedminecraft.polychat3.core.payloads.s2c;

import club.moddedminecraft.polychat3.core.payloads.Payload;

/**
 * A player was muted or unmuted, from Discord or another server. The client updates its copy of the
 * mute list.
 */
public final class MuteS2C extends Payload {
    public static final String TYPE = "mute";

    private final String target;
    private final boolean muted;
    private final String by;

    public MuteS2C(String target, boolean muted, String by) {
        this.target = target;
        this.muted = muted;
        this.by = by;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return Minecraft UUID of the player muted or unmuted, dashed.
     */
    public String getTarget() {
        return target;
    }

    /**
     * @return true if now muted, false if unmuted.
     */
    public boolean isMuted() {
        return muted;
    }

    /**
     * @return who did it: a Minecraft UUID for an in-game mute, a Discord user id for a Discord one.
     */
    public String getBy() {
        return by;
    }
}
