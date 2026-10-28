package club.moddedminecraft.polychat3.core.payloads.s2c;

import java.util.List;

import com.google.gson.annotations.SerializedName;

import club.moddedminecraft.polychat3.core.payloads.Payload;

/**
 * Asks the client to run a console command and answer with {@code commandResult}.
 *
 * <p>The client joins {@code args} with single spaces and substitutes the result for {@code $args}
 * once, in its own override for {@code name} if it has one, else in {@code defaultCommand}.
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
}
