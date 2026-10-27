package club.moddedminecraft.polychat3.core;

/**
 * Constants both ends must agree on. See PROTOCOL.md.
 */
public final class Protocol {
    /** Both ends must use exactly this version. Stays 1 until the first release, then goes up each release. */
    public static final int VERSION = 1;
    /** Largest encoded frame either side accepts, in bytes. */
    public static final int MAX_FRAME_BYTES = 65536;

    /** Client shutting down, after sending {@code serverStatus} {@code stopped} or {@code crashed}. */
    public static final int CLOSE_NORMAL = 1000;
    /** Hub shutting down or restarting. */
    public static final int CLOSE_GOING_AWAY = 1001;
    /** Frame larger than {@link #MAX_FRAME_BYTES}. */
    public static final int CLOSE_TOO_BIG = 1009;
    /** First frame was not a valid {@code handshake}, or none came in time. */
    public static final int CLOSE_BAD_HANDSHAKE = 4000;
    /** Wrong token. */
    public static final int CLOSE_BAD_TOKEN = 4001;
    /** Version differs from {@link #VERSION}. */
    public static final int CLOSE_BAD_VERSION = 4002;
    /** Invalid server id, or one a hub module has claimed. */
    public static final int CLOSE_BAD_SERVER_ID = 4003;
    /** Replaced by a newer connection with the same server id. */
    public static final int CLOSE_REPLACED = 4004;
    /** Binary frame, or three invalid frames in a row. */
    public static final int CLOSE_PROTOCOL_ERROR = 4005;

    private Protocol() {
    }
}
