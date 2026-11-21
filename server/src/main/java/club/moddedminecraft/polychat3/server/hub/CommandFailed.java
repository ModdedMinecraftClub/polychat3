package club.moddedminecraft.polychat3.server.hub;

/**
 * Why a command sent with {@code runCommand} got no {@code commandResult}: it timed out, or its server went
 * offline or unresponsive first. A module's command future completes exceptionally with this.
 */
public final class CommandFailed extends Exception {
    /** @param message what went wrong, for example that the command timed out. */
    public CommandFailed(String message) {
        super(message);
    }
}
