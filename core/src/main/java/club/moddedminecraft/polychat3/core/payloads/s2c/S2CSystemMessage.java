package club.moddedminecraft.polychat3.core.payloads.s2c;

import com.google.gson.annotations.SerializedName;

import club.moddedminecraft.polychat3.core.payloads.Payload;

/**
 * A message from the hub to show in game, such as a broadcast or restart warning.
 */
public final class S2CSystemMessage extends Payload {
    public static final String TYPE = "systemMessage";

    /** What kind of system message it is. */
    public enum Kind {
        @SerializedName("broadcast") BROADCAST,
        @SerializedName("restart") RESTART,
        @SerializedName("info") INFO
    }

    private final String message;
    private final Kind kind;

    public S2CSystemMessage(String message, Kind kind) {
        this.message = message;
        this.kind = kind;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return plain text.
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return what kind of message it is, so the client can colour it.
     */
    public Kind getKind() {
        return kind;
    }
}
