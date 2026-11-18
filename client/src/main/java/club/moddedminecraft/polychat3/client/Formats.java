package club.moddedminecraft.polychat3.client;

import java.util.Locale;

import club.moddedminecraft.polychat3.core.payloads.ServerState;

import computer.heather.goagnostic.api.util.chat.ChatSymbols.Colour;
import computer.heather.goagnostic.api.util.chat.ChatSymbols.Formatting;

/**
 * The lines players see for what other servers send, in each server's colour. Plain text comes in and Minecraft
 * formatting goes out, as PROTOCOL.md "Text" says: the receiver adds its own.
 */
final class Formats {
    private Formats() {
    }

    /** {@code [A5] Steve: hi} */
    static String chat(String colour, String server, String player, String message) {
        return prefix(colour, server) + player + ": " + message;
    }

    /** {@code [A5] Steve joined} */
    static String joined(String colour, String server, String player) {
        return prefix(colour, server) + player + " joined";
    }

    /** {@code [A5] Steve left} */
    static String left(String colour, String server, String player) {
        return prefix(colour, server) + player + " left";
    }

    /** {@code [A5] All the Mods 5 started} */
    static String status(String colour, String server, String serverName, ServerState state) {
        return prefix(colour, server) + serverName + " " + describe(state);
    }

    /** What happened, in words, for a state the server just moved to. */
    private static String describe(ServerState state) {
        switch (state) {
            case STARTING:
                return "is starting";
            case STARTED:
                return "started";
            case STOPPING:
                return "is stopping";
            case STOPPED:
                return "stopped";
            case CRASHED:
                return "crashed";
            case OFFLINE:
                return "went offline";
            case UNRESPONSIVE:
                return "is not responding";
            default:
                // A state added to the protocol later still shows something
                return "is " + state.name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * {@code [A5] } in the server's colour, then a reset so the rest of the line is plain. The tag is the server id in
     * upper case, as PROTOCOL.md "Server ids" says.
     */
    private static String prefix(String colour, String server) {
        return code(colour) + "[" + server.toUpperCase(Locale.ROOT) + "] " + Formatting.RESET;
    }

    /**
     * The § code for a colour name such as {@code dark_blue}. White for a name it doesn't know: the hub doesn't check
     * colours, and one server's typo mustn't break its lines.
     */
    private static String code(String colour) {
        try {
            return Colour.valueOf(colour.toUpperCase(Locale.ROOT)).getCode();
        } catch (IllegalArgumentException e) {
            return Colour.WHITE.getCode();
        }
    }
}
