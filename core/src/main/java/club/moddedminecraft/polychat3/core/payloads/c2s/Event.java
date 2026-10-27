package club.moddedminecraft.polychat3.core.payloads.c2s;

import club.moddedminecraft.polychat3.core.payloads.Payload;

/**
 * A client frame that carries {@code seq} and is acknowledged by the hub.
 */
public abstract class Event extends Payload {
    private final long seq;

    protected Event(long seq) {
        this.seq = seq;
    }

    /**
     * @return 1 for the first event this process sends, plus one for every event after it.
     */
    public long getSeq() {
        return seq;
    }
}
