package club.moddedminecraft.polychat3.core.payloads.c2s;

import club.moddedminecraft.polychat3.core.payloads.Payload;

/**
 * Sent from the game's server tick every 15 s, so the hub knows the main thread is running.
 * Carries no {@code seq} and is never buffered or replayed.
 */
public final class C2SHeartbeat extends Payload {
    public static final String TYPE = "heartbeat";

    @Override
    public String type() {
        return TYPE;
    }
}
