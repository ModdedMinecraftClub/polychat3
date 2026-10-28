package club.moddedminecraft.polychat3.server.hub;

import java.util.HashMap;
import java.util.Map;

/**
 * The last {@code seq} the hub accepted from each game server, as in PROTOCOL.md "Sequence numbers,
 * acknowledgements and replay". Kept across connections, so a reconnect resumes where it left off. Only
 * used on the hub thread.
 */
final class SequenceTracker {

    /** By game server id. Never removed, so a dropped connection keeps its place. */
    private final Map<String, Tracked> servers = new HashMap<>();

    /**
     * Starts tracking a connection after its handshake is accepted.
     *
     * @return the last accepted {@code seq} if {@code session} is the one already tracked for this id,
     * otherwise 0, since a new session starts counting again.
     */
    long resume(String id, long session) {
        Tracked tracked = servers.get(id);
        if (tracked == null || tracked.session != session) {
            tracked = new Tracked(session);
            servers.put(id, tracked);
        }
        return tracked.lastSeq;
    }

    /**
     * @return true if {@code seq} is new and is now the last accepted, false if it is a duplicate. Gaps are
     * accepted.
     */
    boolean accept(String id, long seq) {
        Tracked tracked = tracked(id);
        if (seq <= tracked.lastSeq) {
            return false;
        }
        tracked.lastSeq = seq;
        return true;
    }

    /** @return the last accepted {@code seq} for this id, 0 if none. */
    long last(String id) {
        return tracked(id).lastSeq;
    }

    /** @throws IllegalStateException if {@link #resume} never saw this id, which is a hub bug. */
    private Tracked tracked(String id) {
        Tracked tracked = servers.get(id);
        if (tracked == null) {
            throw new IllegalStateException("No tracked session for " + id);
        }
        return tracked;
    }

    /** One game server's session and the last {@code seq} accepted in it. */
    private static final class Tracked {
        final long session;
        long lastSeq;

        Tracked(long session) {
            this.session = session;
        }
    }

}
