package club.moddedminecraft.polychat3.core.payloads;

/**
 * Sent either way. Before a protocol close it says why; on its own it reports a
 * non-fatal problem, such as one malformed frame.
 */
public final class ProtocolError extends Payload {
    public static final String TYPE = "protocolError";

    private final String code;
    private final String message;

    public ProtocolError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return machine-readable reason, for example {@code unsupported_version}.
     */
    public String getCode() {
        return code;
    }

    /**
     * @return human-readable reason, for the other side's log.
     */
    public String getMessage() {
        return message;
    }
}
