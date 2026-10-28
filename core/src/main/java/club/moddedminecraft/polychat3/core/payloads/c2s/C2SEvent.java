package club.moddedminecraft.polychat3.core.payloads.c2s;

import club.moddedminecraft.polychat3.core.payloads.Payload;

/**
 * A client frame that carries {@code seq} and is acknowledged by the hub.
 */
public abstract class C2SEvent extends Payload {
    private final long seq;

    protected C2SEvent(long seq) {
        this.seq = seq;
    }

    /**
     * @return 1 for the first event this process sends, plus one for every event after it.
     */
    public long getSeq() {
        return seq;
    }
}
