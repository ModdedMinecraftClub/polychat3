package club.moddedminecraft.polychat3.core.payloads;

/**
 * Thrown by {@link PayloadCodec#decode(String)} for a frame that breaks the protocol.
 */
public class ProtocolException extends Exception {
    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
