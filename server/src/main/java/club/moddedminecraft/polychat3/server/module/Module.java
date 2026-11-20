package club.moddedminecraft.polychat3.server.module;

/**
 * A part of the hub that config switches on by name, such as broadcasts. The hub enables the modules named in
 * "modules", in that order, before it accepts connections, and disables them in reverse when it stops.
 */
public interface Module {

    /** The name config's "modules" list uses, for example "broadcasts". */
    String id();

    /** Starts the module. An exception stops the hub from starting. */
    void enable(ModuleContext context) throws Exception;

    /** Stops the module: cancels its timers and stops its threads. Nothing by default. */
    default void disable() {
    }
}
