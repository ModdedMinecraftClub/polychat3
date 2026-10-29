package club.moddedminecraft.polychat3.core.client;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The delays between reconnects, as in PROTOCOL.md "Close codes": 1, 2, 4, 8, 16, then 30 s, each with 20 % jitter so
 * servers don't all reconnect at once after a hub restart. Only used on the session's thread.
 */
final class Backoff {
    private static final long[] STEPS_SECONDS = { 1, 2, 4, 8, 16, 30 };
    private static final double JITTER = 0.2;
    // Index into STEPS_SECONDS; stays on the last step once there
    private int attempt;

    /** @return the next delay in ms, a step longer than the last until it reaches 30 s. */
    long nextMillis() {
        long base = STEPS_SECONDS[attempt] * 1000;
        if (attempt < STEPS_SECONDS.length - 1) {
            attempt++;
        }
        return (long) (base * (1 - JITTER + 2 * JITTER * ThreadLocalRandom.current().nextDouble()));
    }

    /** Starts again from 1 s, after a connection is accepted. */
    void reset() {
        attempt = 0;
    }
}