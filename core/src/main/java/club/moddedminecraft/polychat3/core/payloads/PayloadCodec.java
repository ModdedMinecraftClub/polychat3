package club.moddedminecraft.polychat3.core.payloads;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

import club.moddedminecraft.polychat3.core.payloads.c2s.ChatMessage;
import club.moddedminecraft.polychat3.core.payloads.c2s.CommandResult;
import club.moddedminecraft.polychat3.core.payloads.c2s.Handshake;
import club.moddedminecraft.polychat3.core.payloads.c2s.Heartbeat;
import club.moddedminecraft.polychat3.core.payloads.c2s.PlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.c2s.PlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.c2s.PlayerList;
import club.moddedminecraft.polychat3.core.payloads.c2s.ServerStatus;
import club.moddedminecraft.polychat3.core.payloads.s2c.Acknowledgement;
import club.moddedminecraft.polychat3.core.payloads.s2c.HandshakeAccepted;
import club.moddedminecraft.polychat3.core.payloads.s2c.RunCommand;
import club.moddedminecraft.polychat3.core.payloads.s2c.SystemMessage;

/**
 * Turns payloads into JSON frames and back, so neither the client nor the hub handles JSON itself.
 *
 * <p>Each instance decodes one direction: the hub uses {@link #C2S}, the client uses {@link #S2C}.
 * {@link #encode(Payload)} works for any payload.
 */
public final class PayloadCodec {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping() // keeps < and = one byte each
            .serializeNulls() // so a null field shows up, and is refused, instead of vanishing
            .setStrictness(Strictness.STRICT) // Gson's default reader accepts non-JSON like {type:'x'}
            .create();

    /** Decodes what a client sends. Used by the hub. */
    public static final PayloadCodec C2S = new PayloadCodec()
            .register(Handshake.TYPE, Handshake.class)
            .register(Heartbeat.TYPE, Heartbeat.class)
            .register(ChatMessage.TYPE, ChatMessage.class)
            .register(PlayerJoin.TYPE, PlayerJoin.class)
            .register(PlayerLeave.TYPE, PlayerLeave.class)
            .register(PlayerList.TYPE, PlayerList.class)
            .register(ServerStatus.TYPE, ServerStatus.class)
            .register(CommandResult.TYPE, CommandResult.class)
            .register(ProtocolError.TYPE, ProtocolError.class);

    /** Decodes what the hub sends. Used by the client. */
    public static final PayloadCodec S2C = new PayloadCodec()
            .register(HandshakeAccepted.TYPE, HandshakeAccepted.class)
            .register(Acknowledgement.TYPE, Acknowledgement.class)
            .register(club.moddedminecraft.polychat3.core.payloads.s2c.ChatMessage.TYPE,
                    club.moddedminecraft.polychat3.core.payloads.s2c.ChatMessage.class)
            .register(club.moddedminecraft.polychat3.core.payloads.s2c.PlayerJoin.TYPE,
                    club.moddedminecraft.polychat3.core.payloads.s2c.PlayerJoin.class)
            .register(club.moddedminecraft.polychat3.core.payloads.s2c.PlayerLeave.TYPE,
                    club.moddedminecraft.polychat3.core.payloads.s2c.PlayerLeave.class)
            .register(club.moddedminecraft.polychat3.core.payloads.s2c.PlayerList.TYPE,
                    club.moddedminecraft.polychat3.core.payloads.s2c.PlayerList.class)
            .register(club.moddedminecraft.polychat3.core.payloads.s2c.ServerStatus.TYPE,
                    club.moddedminecraft.polychat3.core.payloads.s2c.ServerStatus.class)
            .register(RunCommand.TYPE, RunCommand.class)
            .register(SystemMessage.TYPE, SystemMessage.class)
            .register(ProtocolError.TYPE, ProtocolError.class);

    private final Map<String, Class<? extends Payload>> types = new HashMap<>();

    private PayloadCodec() {
    }

    private PayloadCodec register(String type, Class<? extends Payload> payloadClass) {
        types.put(type, payloadClass);
        return this;
    }

    /**
     * Encodes a payload as a JSON frame, with {@code type} first.
     *
     * @param payload the payload to send
     * @return the frame, ready to send
     * @throws IllegalArgumentException if any field is null, which would break the protocol
     */
    public static String encode(Payload payload) {
        JsonObject frame = toFrame(payload);
        if (hasNull(frame)) {
            throw new IllegalArgumentException("null field in " + GSON.toJson(frame));
        }
        return GSON.toJson(frame);
    }

    /**
     * Decodes a frame sent in this codec's direction.
     *
     * @param frame the frame as received
     * @return the payload; check its class to handle it
     * @throws ProtocolException if the frame breaks the protocol
     */
    public Payload decode(String frame) throws ProtocolException {
        JsonObject json = parse(frame);
        Class<? extends Payload> type = typeOf(json);

        Payload payload;
        try {
            payload = GSON.fromJson(json, type);
        } catch (JsonParseException e) {
            throw new ProtocolException("bad frame: " + frame, e);
        }

        // Gson is lenient: it ignores unknown fields, defaults missing ones and converts between types.
        // A frame that follows the protocol encodes back to the same JSON values as we received.
        JsonObject expected = toFrame(payload);
        if (hasNull(expected) || !expected.equals(json)) {
            throw new ProtocolException("frame doesn't match its type: got " + frame
                    + ", expected " + GSON.toJson(expected));
        }
        return payload;
    }

    private static JsonObject parse(String frame) throws ProtocolException {
        JsonElement json;
        try {
            json = GSON.fromJson(frame, JsonElement.class); // also refuses data after the value
        } catch (JsonParseException e) {
            throw new ProtocolException("not JSON: " + frame, e);
        }
        if (!(json instanceof JsonObject)) { // null for an empty frame
            throw new ProtocolException("not a JSON object: " + frame);
        }
        return (JsonObject) json;
    }

    private Class<? extends Payload> typeOf(JsonObject json) throws ProtocolException {
        JsonElement type = json.get("type");
        Class<? extends Payload> payloadClass = null;
        if (type != null && type.isJsonPrimitive()) {
            payloadClass = types.get(type.getAsString());
        }
        if (payloadClass == null) {
            throw new ProtocolException("missing or unknown type: " + type);
        }
        return payloadClass;
    }

    private static JsonObject toFrame(Payload payload) {
        JsonObject frame = new JsonObject();
        frame.addProperty("type", payload.type());
        for (Map.Entry<String, JsonElement> field : GSON.toJsonTree(payload).getAsJsonObject().entrySet()) {
            frame.add(field.getKey(), field.getValue());
        }
        return frame;
    }

    private static boolean hasNull(JsonElement element) {
        if (element.isJsonObject()) {
            return element.getAsJsonObject().asMap().values().stream().anyMatch(PayloadCodec::hasNull);
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray().asList().stream().anyMatch(PayloadCodec::hasNull);
        }
        return element.isJsonNull();
    }
}
