package club.moddedminecraft.polychat3.server.hub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import club.moddedminecraft.polychat3.core.payloads.c2s.C2SCommandResult;

/**
 * The commands the hub sent with {@code runCommand} that have no {@code commandResult} yet. Gives each one an
 * id and completes its future when the answer comes. Never reads the clock: the caller passes
 * {@link System#nanoTime()} in. Only used on the hub thread.
 */
final class CommandBroker {
    /** How long a server has to answer, in {@link System#nanoTime()} units. */
    private final long timeoutNanos;
    /** By command id. */
    private final Map<String, Pending> pending = new HashMap<>();

    /** The number in the last id given out, so ids are {@code c-1}, {@code c-2} and so on. */
    private long lastId;

    /** @param timeoutNanos how long a server has to answer, in {@link System#nanoTime()} units. */
    CommandBroker(long timeoutNanos) {
        this.timeoutNanos = timeoutNanos;
    }

    /**
     * Records a command about to be sent and gives it a new id.
     *
     * @param server the game server that will run it.
     * @param now {@link System#nanoTime()} when it is sent; the timeout counts from here.
     * @param result completed when the server answers, or exceptionally with {@link CommandFailed}.
     * @return the id to put in the {@code runCommand} frame.
     */
    String start(String server, long now, CompletableFuture<C2SCommandResult> result) {
        String id = "c-" + (++lastId);
        pending.put(id, new Pending(server, now + timeoutNanos, result));
        return id;

    }

    /**
     * Completes the command a {@code commandResult} answers. Ignores an id that isn't pending (it timed out, or
     * was never given out) and an answer from a server the command wasn't sent to.
     *
     * @param server the game server that sent the answer.
     */
    void result(String server, C2SCommandResult message) {
        Pending waiting = pending.get(message.getId());
        if (waiting == null || !waiting.server.equals(server)) {
            return;
        }
        pending.remove(message.getId());
        waiting.result.complete(message);
    }

    /**
     * Fails every command whose server didn't answer in time.
     *
     * @param now {@link System#nanoTime()} now.
     */
    void expire(long now) {
        removeAndFail(p -> now - p.deadline > 0,
                "no answer within " + TimeUnit.NANOSECONDS.toSeconds(timeoutNanos) + " s");
    }

    /**
     * Fails every command still waiting on one server, because it went offline or unresponsive.
     *
     * @param reason why, for the {@link CommandFailed} message, for example {@code "a5 went offline"}.
     */
    void fail(String server, String reason) {
        removeAndFail(p -> p.server.equals(server), reason);
    }

    /**
     * Removes the pending commands that match, then fails each one with {@code reason}. Fails them only after
     * removing all, because failing runs the module's code at once, and that code may call {@link #start}.
     */
    private void removeAndFail(Predicate<Pending> match, String reason) {
        List<Pending> failed = new ArrayList<>();
        pending.values().removeIf(command -> {
            if (match.test(command)) {
                failed.add(command);
                return true;
            }
            return false;
        });
        for (Pending command : failed) {
            command.result.completeExceptionally(new CommandFailed(reason));
        }
    }

    /** A command waiting for its answer. */
    private static final class Pending {
        final String server;
        final long deadline;
        final CompletableFuture<C2SCommandResult> result;

        Pending(String server, long deadline, CompletableFuture<C2SCommandResult> result) {
            this.server = server;
            this.deadline = deadline;
            this.result = result;
        }
    }
}
