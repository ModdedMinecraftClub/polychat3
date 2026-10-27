package club.moddedminecraft.polychat3.core.payloads;

/**
 * One frame on the wire. {@link PayloadCodec} writes {@link #type()} as the frame's {@code type} field.
 */
public abstract class Payload {
    /**
     * @return the frame's {@code type}: the class name in camelCase, for example {@code playerJoin}
     */
    public abstract String type();
}
