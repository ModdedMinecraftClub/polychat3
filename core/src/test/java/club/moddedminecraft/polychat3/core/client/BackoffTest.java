package club.moddedminecraft.polychat3.core.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BackoffTest {
    /** 1, 2, 4, 8, 16, then 30 s for good, each within 20 % either way. */
    @Test
    void delaysGrowThenStayAt30Seconds() {
        // Jitter is random, so check many runs
        for (int run = 0; run < 100; run++) {
            Backoff backoff = new Backoff();
            for (long seconds : new long[] {1, 2, 4, 8, 16, 30, 30, 30}) {
                assertWithinJitter(seconds, backoff.nextMillis());
            }
        }
    }

    @Test
    void resetStartsAgainFromOneSecond() {
        Backoff backoff = new Backoff();
        for (int i = 0; i < 4; i++) {
            backoff.nextMillis();
        }
        backoff.reset();
        assertWithinJitter(1, backoff.nextMillis());
        assertWithinJitter(2, backoff.nextMillis());
    }

    private static void assertWithinJitter(long seconds, long millis) {
        assertTrue(millis >= seconds * 800 && millis <= seconds * 1200, millis + " ms is not " + seconds + " s ± 20 %");
    }
}
