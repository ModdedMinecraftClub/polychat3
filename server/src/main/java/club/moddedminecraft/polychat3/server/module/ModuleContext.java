package club.moddedminecraft.polychat3.server.module;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import club.moddedminecraft.polychat3.core.config.ConfigLoader;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SCommandResult;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CSystemMessage;
import club.moddedminecraft.polychat3.server.hub.Hub;

/**
 * What one module may do with the hub. Safe to call from any thread: each call only posts to the hub thread, and
 * timers run there.
 */
public final class ModuleContext {
    private final Hub hub;
    /** The module's id, which names its config file. */
    private final String id;

    ModuleContext(Hub hub, String id) {
        this.hub = hub;
        this.id = id;
    }

    /** Sends a systemMessage to every connected game server. */
    public void systemMessage(String message, S2CSystemMessage.Kind kind) {
        hub.systemMessage(message, kind);
    }

    /**
     * Runs a command on one game server.
     *
     * @return completed on the hub thread with the server's commandResult, or exceptionally with CommandFailed.
     * @see Hub#runCommand
     */
    public CompletableFuture<C2SCommandResult> runCommand(String server, String name, String defaultCommand,
            List<String> args) {
        return hub.runCommand(server, name, defaultCommand, args);
    }

    /**
     * Runs a task on the hub thread every period, the first time one period from now.
     *
     * @return its handle; cancel(false) stops it, for example in the module's disable().
     */
    public ScheduledFuture<?> every(long period, TimeUnit unit, Runnable runnable) {
        return hub.every(period, unit, runnable);
    }

    /**
     * The module's config, from config/{id}.jsonc. A missing file is first copied from /defaults/{id}.jsonc in the
     * jar, as for the hub's own config.
     */
    public <T> T config(Class<T> type) throws IOException {
        return ConfigLoader.load(Paths.get("config/" + id + ".jsonc"), type, "/defaults/" + id + ".jsonc");
    }
}
