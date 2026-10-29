package club.moddedminecraft.polychat3.core.client;

/**
 * What the mod gives {@link ClientSession} to talk to the hub: one WebSocket at a time. Every method returns at once
 * and may be called from the session's thread.
 */
public interface Transport {

    /**
     * Opens a new connection to the hub, in the background. The socket then calls the session's {@code onOpen()}, or
     * {@code onClose(...)} if it fails. Never called while a connection is open.
     */
    void connect();

    /** Sends one text frame. Does nothing if the connection is already closed. */
    void send(String frame);

    /** Closes the connection with a close code from {@code Protocol}. */
    void close(int code);
}
