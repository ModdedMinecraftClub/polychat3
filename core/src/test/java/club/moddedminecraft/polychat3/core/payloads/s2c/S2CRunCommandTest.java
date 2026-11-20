package club.moddedminecraft.polychat3.core.payloads.s2c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/** PROTOCOL.md "runCommand": how the client builds the console command from a runCommand. */
class S2CRunCommandTest {
    private static S2CRunCommand run(String defaultCommand, String... args) {
        return new S2CRunCommand("c-1", "test", defaultCommand, Arrays.asList(args));
    }

    @Test
    void joinsArgsWithSpacesInPlaceOfArgs() {
        assertEquals("forge tps 0 1", run("forge tps $args", "0", "1").commandLine(null));
    }

    @Test
    void usesTheOverrideWhenThereIsOne() {
        assertEquals("neoforge tps 0", run("forge tps $args", "0").commandLine("neoforge tps $args"));
    }

    @Test
    void leavesACommandWithoutArgsUnchanged() {
        assertEquals("list", run("list", "ignored").commandLine(null));
    }

    @Test
    void emptyArgsLeaveNoTrailingSpace() {
        S2CRunCommand message = new S2CRunCommand("c-1", "tps", "forge tps $args", Collections.emptyList());
        assertEquals("forge tps", message.commandLine(null));
    }

    @Test
    void replacesOnlyTheFirstArgs() {
        assertEquals("say hi $args", run("say $args $args", "hi").commandLine(null));
    }

    @Test
    void dollarsAndBackslashesInArgsArePlainText() {
        assertEquals("say costs $5 \\o/ $args", run("say $args", "costs", "$5", "\\o/", "$args").commandLine(null));
    }
}
