package club.moddedminecraft.polychat3.core.payloads.s2c;

import club.moddedminecraft.polychat3.core.payloads.Payload;

/**
 * Confirms the hub accepted the client's events.
 */
public final class Acknowledgement extends Payload {
    public static final String TYPE = "acknowledgement";

    private final long seq;

    public Acknowledgement(long seq) {
        this.seq = seq;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return every event up to and including this {@code seq} is accepted.
     */
    public long getSeq() {
        return seq;
    }
}
