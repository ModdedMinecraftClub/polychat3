package club.moddedminecraft.polychat3.core.payloads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import club.moddedminecraft.polychat3.core.payloads.c2s.PlayerListC2S;

class PayloadCodecTest {
    /** Every example frame in PROTOCOL.md decodes, and encodes back to the same text. */
    @Test
    void protocolExamplesRoundTrip() throws IOException, ProtocolException {
        List<String> lines = Files.readAllLines(Paths.get("../PROTOCOL.md"), StandardCharsets.UTF_8);
        int examples = 0;
        for (String line : lines) {
            PayloadCodec codec = line.startsWith("> {") ? PayloadCodec.C2S
                    : line.startsWith("< {") ? PayloadCodec.S2C : null;
            if (codec == null) {
                continue;
            }
            String frame = line.substring(2);
            assertEquals(frame, PayloadCodec.encode(codec.decode(frame)));
            examples++;
        }
        assertFalse(examples == 0, "no example frames found in PROTOCOL.md");
    }

    @Test
    void rejectsFramesThatBreakTheProtocol() {
        String[] frames = {
                "{type:'heartbeat'}",
                "{\"type\":\"heartbeat\"} x",
                "[1]",
                "{\"type\":\"pong\"}",
                "{\"type\":\"commandResult\",\"id\":\"x\",\"ok\":\"yes\",\"output\":\"o\",\"truncated\":false,\"seq\":4}",
                "{\"type\":\"commandResult\",\"id\":\"x\",\"ok\":true,\"output\":\"o\",\"truncated\":false,\"seq\":\"4\"}",
                "{\"type\":\"chatMessage\",\"player\":5,\"senderId\":\"u\",\"message\":\"m\",\"seq\":1}",
                "{\"type\":\"chatMessage\",\"player\":\"p\",\"senderId\":null,\"message\":\"m\",\"seq\":1}",
                "{\"type\":\"chatMessage\",\"player\":\"p\",\"senderId\":\"u\",\"message\":\"m\",\"seq\":1,\"x\":1}",
                "{\"type\":\"chatMessage\",\"player\":\"p\",\"senderId\":\"u\",\"message\":\"m\"}",
                "{\"type\":\"playerJoin\",\"player\":\"p\",\"uuid\":\"u\",\"online\":3000000000,\"seq\":1}",
                "{\"type\":\"serverStatus\",\"state\":\"bogus\",\"seq\":3}",
                "{\"type\":\"playerList\",\"players\":[null],\"seq\":1}",
                "{\"type\":\"playerList\",\"players\":[{\"name\":\"a\",\"uuid\":\"u\",\"x\":1}],\"seq\":1}",
                "{\"type\":\"playerList\",\"players\":[{\"name\":\"a\"}],\"seq\":1}",
        };
        for (String frame : frames) {
            assertThrows(ProtocolException.class, () -> PayloadCodec.C2S.decode(frame), frame);
        }
    }

    @Test
    void rejectsNestedFieldMissingInHubFrame() {
        String frame = "{\"type\":\"handshakeAccepted\",\"version\":1,\"session\":1,\"lastSeq\":0,\"servers\":[{\"id\":\"g\","
                + "\"name\":\"G\",\"colour\":\"gold\",\"address\":\"a\",\"state\":\"started\",\"players\":[]}]}";
        assertThrows(ProtocolException.class, () -> PayloadCodec.S2C.decode(frame));
    }

    @Test
    void encodeRejectsNullFields() {
        assertThrows(IllegalArgumentException.class, () -> PayloadCodec.encode(new ProtocolError(null, "m")));
        assertThrows(IllegalArgumentException.class, () -> PayloadCodec.encode(
                new PlayerListC2S(1, Collections.singletonList(new PlayerInfo("a", null)))));
    }
}
