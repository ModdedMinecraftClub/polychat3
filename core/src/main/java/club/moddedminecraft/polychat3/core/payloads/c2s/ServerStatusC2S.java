package club.moddedminecraft.polychat3.core.payloads.c2s;

import club.moddedminecraft.polychat3.core.payloads.ServerState;

/**
 * This server's lifecycle state changed.
 */
public final class ServerStatusC2S extends Event {
    public static final String TYPE = "serverStatus";

    private final ServerState state;

    public ServerStatusC2S(long seq, ServerState state) {
        super(seq);
        this.state = state;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return {@code STARTING}, {@code STARTED}, {@code STOPPING}, {@code STOPPED} or {@code CRASHED};
     * never {@code OFFLINE} or {@code UNRESPONSIVE}, which only the hub sets.
     */
    public ServerState getState() {
        return state;
    }
}
