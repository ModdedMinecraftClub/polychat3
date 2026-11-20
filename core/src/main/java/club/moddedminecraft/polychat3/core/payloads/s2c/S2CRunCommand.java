package club.moddedminecraft.polychat3.core.payloads.s2c;

import java.util.List;
import java.util.regex.Matcher;

import com.google.gson.annotations.SerializedName;

import club.moddedminecraft.polychat3.core.payloads.Payload;

/**
 * Asks the client to run a console command and answer with {@code commandResult}.
 *
 * <p>The client joins {@code args} with single spaces and substitutes the result for {@code $args}
 * once, in its own override for {@code name} if it has one, else in {@code defaultCommand}, and trims
 * spaces from both ends.
 */
public final class S2CRunCommand extends Payload {
    public static final String TYPE = "runCommand";

    private final String id;
    private final String name;
    @SerializedName("default")
    private final String defaultCommand;
    private final List<String> args;

    public S2CRunCommand(String id, String name, String defaultCommand, List<String> args) {
        this.id = id;
        this.name = name;
        this.defaultCommand = defaultCommand;
        this.args = args;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return chosen by the hub; echoed in {@code commandResult}.
     */
    public String getId() {
        return id;
    }

    /**
     * @return command name, for example {@code tps}.
     */
    public String getName() {
        return name;
    }

    /**
     * @return console command to run if the client has no override for {@code name}.
     */
    public String getDefaultCommand() {
        return defaultCommand;
    }

    /**
     * @return arguments, substituted for {@code $args}.
     */
    public List<String> getArgs() {
        return args;
    }

    /**
     * The console command to run, as PROTOCOL.md "runCommand" says: {@code args} joined with single spaces, put in
     * place of the first {@code $args} in the override, or in {@code defaultCommand} when there is no override,
     * with spaces trimmed from both ends.
     *
     * @param override this server's own command for {@code name}, or null to use {@code defaultCommand}.
     * @return the command line to run.
     */
    public String commandLine(String override) {
        // quoteReplacement: a $ or \ in the args is plain text, not a regex group or escape
        String command = override != null ? override : defaultCommand;
        return command.replaceFirst("\\$args", Matcher.quoteReplacement(String.join(" ", args))).trim();
    }
}
