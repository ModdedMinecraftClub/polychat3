package club.moddedminecraft.polychat3.core.payloads.s2c;

import java.util.List;

import club.moddedminecraft.polychat3.core.payloads.Payload;

/**
 * The hub's answer to a valid {@code handshake}.
 */
public final class HandshakeAccepted extends Payload {
    public static final String TYPE = "handshakeAccepted";

    private final int version;
    private final long lastSeq;
    private final List<ServerEntry> servers;

    public HandshakeAccepted(int version, long lastSeq, List<ServerEntry> servers) {
        this.version = version;
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
     * @return highest {@code seq} the hub accepted from this server and session, 0 if none. The client
     * resends its buffered events above it.
     */
    public long getLastSeq() {
        return lastSeq;
    }

    /**
     * @return every server the hub knows, so the client can show who is online elsewhere.
     */
    public List<ServerEntry> getServers() {
        return servers;
    }
}
