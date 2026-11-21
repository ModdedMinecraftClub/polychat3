package club.moddedminecraft.polychat3.server.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import club.moddedminecraft.polychat3.core.payloads.c2s.C2SCommandResult;

/** Ids, answers, timeouts and failing a server's commands, with made-up {@code now} values. */
class CommandBrokerTest {
    private static final long TIMEOUT = TimeUnit.SECONDS.toNanos(10);

    private final CommandBroker broker = new CommandBroker(TIMEOUT);

    @Test
    void idsCountUp() {
        assertEquals("c-1", broker.start("a5", 0, new CompletableFuture<>()));
        assertEquals("c-2", broker.start("b2", 0, new CompletableFuture<>()));
        assertEquals("c-3", broker.start("a5", 0, new CompletableFuture<>()));
    }

    @Test
    void anAnswerCompletesItsCommand() throws Exception {
        CompletableFuture<C2SCommandResult> first = new CompletableFuture<>();
        CompletableFuture<C2SCommandResult> second = new CompletableFuture<>();
        broker.start("a5", 0, first);
        String id = broker.start("a5", 0, second);

        C2SCommandResult answer = answer(id);
        broker.result("a5", answer);

        assertSame(answer, second.get());
        assertFalse(first.isDone());
    }

    /** A server can't answer a command that was sent to another one. */
    @Test
    void ignoresAnAnswerFromAnotherServer() {
        CompletableFuture<C2SCommandResult> future = new CompletableFuture<>();
        String id = broker.start("a5", 0, future);

        broker.result("b2", answer(id));

        assertFalse(future.isDone());
        broker.result("a5", answer(id));
        assertTrue(future.isDone());
    }

    @Test
    void ignoresAnUnknownId() {
        CompletableFuture<C2SCommandResult> future = new CompletableFuture<>();
        broker.start("a5", 0, future);

        broker.result("a5", answer("c-99"));

        assertFalse(future.isDone());
    }

    /** The first answer wins; a second one for the same id finds nothing pending. */
    @Test
    void ignoresASecondAnswer() throws Exception {
        CompletableFuture<C2SCommandResult> future = new CompletableFuture<>();
        String id = broker.start("a5", 0, future);
        C2SCommandResult first = answer(id);

        broker.result("a5", first);
        broker.result("a5", answer(id));

        assertSame(first, future.get());
    }

    @Test
    void expiresOnlyAfterTheTimeout() {
        CompletableFuture<C2SCommandResult> future = new CompletableFuture<>();
        broker.start("a5", 1_000, future);

        broker.expire(1_000 + TIMEOUT);
        assertFalse(future.isDone());

        broker.expire(1_000 + TIMEOUT + 1);
        assertTrue(failure(future).getMessage().contains("10 s"), failure(future).getMessage());
    }

    @Test
    void expireLeavesNewerCommandsWaiting() {
        CompletableFuture<C2SCommandResult> old = new CompletableFuture<>();
        CompletableFuture<C2SCommandResult> recent = new CompletableFuture<>();
        broker.start("a5", 0, old);
        broker.start("a5", TIMEOUT, recent);

        broker.expire(TIMEOUT + 1);

        assertTrue(old.isCompletedExceptionally());
        assertFalse(recent.isDone());
    }

    /** {@link System#nanoTime()} can wrap past {@link Long#MAX_VALUE}; only differences are compared. */
    @Test
    void expiresAcrossNanoTimeWrapping() {
        CompletableFuture<C2SCommandResult> future = new CompletableFuture<>();
        long now = Long.MAX_VALUE - 5;
        broker.start("a5", now, future);

        broker.expire(Long.MAX_VALUE);
        assertFalse(future.isDone());

        broker.expire(now + TIMEOUT + 1); // wraps to a negative number
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void failsOnlyThatServersCommands() {
        CompletableFuture<C2SCommandResult> a5 = new CompletableFuture<>();
        CompletableFuture<C2SCommandResult> b2 = new CompletableFuture<>();
        broker.start("a5", 0, a5);
        broker.start("b2", 0, b2);

        broker.fail("a5", "a5 went offline");

        assertEquals("a5 went offline", failure(a5).getMessage());
        assertFalse(b2.isDone());
    }

    /** A failed command is gone, so a late answer for it changes nothing. */
    @Test
    void ignoresAnAnswerAfterFailing() {
        CompletableFuture<C2SCommandResult> future = new CompletableFuture<>();
        String id = broker.start("a5", 0, future);

        broker.fail("a5", "a5 went offline");
        broker.result("a5", answer(id));

        assertInstanceOf(CommandFailed.class, failure(future));
    }

    /** Failing runs the module's code at once; starting a new command from there must not break the loop. */
    @Test
    void aModuleCanStartACommandWhenOneFails() {
        CompletableFuture<C2SCommandResult> first = new CompletableFuture<>();
        CompletableFuture<C2SCommandResult> retry = new CompletableFuture<>();
        broker.start("a5", 0, first);
        broker.start("a5", 0, new CompletableFuture<>());
        first.whenComplete((result, error) -> broker.start("a5", 2 * TIMEOUT, retry));

        broker.expire(TIMEOUT + 1);

        assertTrue(first.isCompletedExceptionally());
        broker.expire(2 * TIMEOUT);
        assertFalse(retry.isDone()); // Started after the loop, still waiting
    }

    private static C2SCommandResult answer(String id) {
        return new C2SCommandResult(1, id, true, "TPS: 20.0", false);
    }

    /** The exception a failed future holds. */
    private static Throwable failure(CompletableFuture<C2SCommandResult> future) {
        assertTrue(future.isDone(), "not failed yet");
        return assertThrows(ExecutionException.class, future::get).getCause();
    }
}
