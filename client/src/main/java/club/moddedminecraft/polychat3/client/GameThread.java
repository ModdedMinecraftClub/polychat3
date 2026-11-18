package club.moddedminecraft.polychat3.client;

import java.util.concurrent.ConcurrentLinkedQueue;

import computer.heather.goagnostic.api.baseclasses.GoAgnosticMod;

/**
 * Work for the game thread, posted from any thread and run at the start of the next server tick. Go!Agnostic has no
 * way to run code on the game thread, so the mod keeps its own queue.
 */
final class GameThread {
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final GoAgnosticMod.Logger logger;

    GameThread(GoAgnosticMod.Logger logger) {
        this.logger = logger;
    }

    /** Runs task on the game thread at the next tick. Safe from any thread. */
    void post(Runnable task) {
        tasks.add(task);
    }

    /** Runs every task posted so far. Game thread only: serverTick calls it. */
    void runAll() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                // A broken task mustn't crash the server tick it runs in; log it and run the next
                logger.error("game thread task failed: " + e);
            }
        }
    }
}
