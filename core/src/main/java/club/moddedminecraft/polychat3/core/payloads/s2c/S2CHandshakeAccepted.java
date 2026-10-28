package club.moddedminecraft.polychat3.core.payloads.s2c;

import java.util.List;

import club.moddedminecraft.polychat3.core.payloads.Payload;

/**
 * The hub's answer to a valid {@code handshake}.
 */
public final class S2CHandshakeAccepted extends Payload {
    public static final String TYPE = "handshakeAccepted";

    private final int version;
    private final long session;
    private final long lastSeq;
    private final List<S2CServerEntry> servers;

    public S2CHandshakeAccepted(int version, long session, long lastSeq, List<S2CServerEntry> servers) {
        this.version = version;
        this.session = session;
        this.lastSeq = lastSeq;
        this.servers = servers;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return protocol version in use.
     */
    public int getVersion() {
        return version;
    }

    /**
     * @return the hub's process start time in ms. The client sends it back as {@code hubSession} when it
     * reconnects, so a restarted hub doesn't replay against a stale {@code lastRelayed}.
     */
    public long getSession() {
        return session;
    }

    /**
     * @return highest {@code seq} the hub accepted from this server and session, 0 if none. The client
     * resends its buffered events above it.
     */
    public long getLastSeq() {
        return lastSeq;
    }

    /**
     * @return every server the hub knows, so the client can show who is online elsewhere.
     */
    public List<S2CServerEntry> getServers() {
        return servers;
    }
}
