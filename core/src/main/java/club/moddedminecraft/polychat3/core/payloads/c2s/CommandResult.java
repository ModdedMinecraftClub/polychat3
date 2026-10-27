package club.moddedminecraft.polychat3.core.payloads.c2s;

/**
 * The output of a console command the hub asked for with {@code runCommand}.
 */
public final class CommandResult extends Event {
    public static final String TYPE = "commandResult";

    private final String id;
    private final boolean ok;
    private final String output;
    private final boolean truncated;

    public CommandResult(long seq, String id, boolean ok, String output, boolean truncated) {
        super(seq);
        this.id = id;
        this.ok = ok;
        this.output = output;
        this.truncated = truncated;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * @return the id of the {@code runCommand} this answers.
     */
    public String getId() {
        return id;
    }

    /**
     * @return whether the command ran.
     */
    public boolean isOk() {
        return ok;
    }

    /**
     * @return captured console output.
     */
    public String getOutput() {
        return output;
    }

    /**
     * @return whether {@code output} was cut to keep the frame under {@code Protocol.MAX_FRAME_BYTES}.
     */
    public boolean isTruncated() {
        return truncated;
    }
}
