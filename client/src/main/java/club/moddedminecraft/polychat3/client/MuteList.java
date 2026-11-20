package club.moddedminecraft.polychat3.client;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * This server's copy of the hub's mute list, by lowercase Minecraft UUID. The session's thread writes it, the game
 * thread reads it, so every write builds a new set and swaps it in whole.
 */
final class MuteList {
    /** The muted UUIDs. Never changed in place: a write replaces it. Volatile so the game thread sees the new set. */
    private volatile Set<String> muted = new HashSet<>();

    /** Replaces the whole list, from muteList after each handshake. Session thread only. */
    void replace(List<String> uuids) {
        muted = new HashSet<>(uuids);
    }

    /** Mutes or unmutes one player, from an s2c mute. Session thread only. */
    void set(String uuid, boolean mute) {
        Set<String> copy = new HashSet<>(muted);
        if (mute) {
            copy.add(uuid);
        } else {
            copy.remove(uuid);
        }
        muted = copy;
    }

    /** True if this player is muted. Any thread. */
    boolean isMuted(String uuid) {
        return muted.contains(uuid.toLowerCase(Locale.ROOT));
    }
}
