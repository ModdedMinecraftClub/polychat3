package club.moddedminecraft.polychat3.core.payloads.c2s;

/**
 * A staff member on this server muted or unmuted a player across the network.
 */
public final class C2SMute extends C2SEvent {
    public static final String TYPE = "mute";

    private final String target;
    private final boolean muted;
    private final String by;

    public C2SMute(long seq, String target, boolean muted, String by) {
        super(seq);
        this.target = target;
        this.muted = muted;
        this.by = by;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return Minecraft UUID of the player being muted or unmuted, dashed.
     */
    public String getTarget() {
        return target;
    }

    /**
     * @return true to mute, false to unmute.
     */
    public boolean isMuted() {
        return muted;
    }

    /**
     * @return Minecraft UUID of the staff member who did it, for the hub's log.
     */
    public String getBy() {
        return by;
    }
}
