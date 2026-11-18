package club.moddedminecraft.polychat3.server.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import club.moddedminecraft.polychat3.core.payloads.PlayerInfo;
import club.moddedminecraft.polychat3.core.payloads.ServerState;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SHandshake;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerList;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SServerStatus;

/** The {@code unresponsive} rules from PROTOCOL.md "Keepalive", with made-up {@code now} values, and the player list. */
class RosterTest {
    /** Any start time works: {@link Roster} only ever subtracts two times. */
    private static final long T0 = 1_000_000_000L;
    private static final long QUIET = seconds(Roster.QUIET_SECONDS);

    private final Roster roster = new Roster();

    @Test
    void marksAfterTheQuietTimeNotAtIt() {
        roster.connected(handshake("a", ServerState.STARTED), T0);
        assertEquals(Collections.emptyList(), roster.unresponsive(T0 + QUIET), "exactly 45 s is still fine");
        assertEquals(Collections.singletonList("a"), roster.unresponsive(T0 + QUIET + 1));
        assertEquals(ServerState.UNRESPONSIVE, roster.entry("a").getState());
    }

    @Test
    void marksStartedAndStoppingSorted() {
        roster.connected(handshake("b", ServerState.STOPPING), T0);
        roster.connected(handshake("a", ServerState.STARTED), T0);
        assertEquals(Arrays.asList("a", "b"), roster.unresponsive(T0 + QUIET + 1));
    }

    @Test
    void skipsEveryOtherState() {
        roster.connected(handshake("starting", ServerState.STARTING), T0);
        roster.connected(handshake("stopped", ServerState.STOPPED), T0);
        roster.connected(handshake("crashed", ServerState.CRASHED), T0);
        roster.connected(handshake("offline", ServerState.STARTED), T0);
        roster.disconnected("offline");
        assertEquals(Collections.emptyList(), roster.unresponsive(T0 + seconds(600)));
        assertEquals(ServerState.STARTING, roster.entry("starting").getState(), "a loading server sends nothing");
    }

    @Test
    void marksOnlyOnce() {
        roster.connected(handshake("a", ServerState.STARTED), T0);
        assertEquals(Collections.singletonList("a"), roster.unresponsive(T0 + QUIET + 1));
        assertEquals(Collections.emptyList(), roster.unresponsive(T0 + seconds(600)), "already unresponsive");
    }

    @Test
    void anyFrameResetsTheQuietTime() {
        roster.connected(handshake("a", ServerState.STARTED), T0);
        assertFalse(roster.heard("a", T0 + seconds(40)), "state didn't change");
        assertEquals(Collections.emptyList(), roster.unresponsive(T0 + seconds(50)));
        assertEquals(Collections.singletonList("a"), roster.unresponsive(T0 + seconds(40) + QUIET + 1));
    }

    @Test
    void heardRestoresTheLastReportedStateOnce() {
        roster.connected(handshake("a", ServerState.STOPPING), T0);
        roster.unresponsive(T0 + QUIET + 1);
        long later = T0 + seconds(60);
        assertTrue(roster.heard("a", later));
        assertEquals(ServerState.STOPPING, roster.entry("a").getState());
        assertFalse(roster.heard("a", later + 1), "already restored");
    }

    @Test
    void serverStatusWhileUnresponsiveSetsBothStates() {
        roster.connected(handshake("a", ServerState.STARTED), T0);
        roster.unresponsive(T0 + QUIET + 1);
        // Hub.receive calls heard before update, so a serverStatus first restores the old state
        assertTrue(roster.heard("a", T0 + seconds(60)));
        roster.update("a", new C2SServerStatus(1, ServerState.STOPPING));
        assertEquals(ServerState.STOPPING, roster.entry("a").getState());

        // lastState moved too: the next restore brings back stopping, not started
        roster.unresponsive(T0 + seconds(60) + QUIET + 1);
        assertTrue(roster.heard("a", T0 + seconds(200)));
        assertEquals(ServerState.STOPPING, roster.entry("a").getState());
    }

    @Test
    void worksWhenNanoTimeWrapsAround() {
        long nearMax = Long.MAX_VALUE - seconds(10);
        roster.connected(handshake("a", ServerState.STARTED), nearMax);
        assertEquals(Collections.emptyList(), roster.unresponsive(nearMax + seconds(20)), "wrapped, 20 s later");
        assertEquals(Collections.singletonList("a"), roster.unresponsive(nearMax + QUIET + 1));
    }

    @Test
    void joinOfAPlayerAlreadyListedKeepsOneEntry() {
        // A mod's playerList can already hold the player whose playerJoin goes out after it
        String alex = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
        roster.connected(handshake("a", ServerState.STARTED), T0);
        roster.update("a", new C2SPlayerList(1, Collections.singletonList(new PlayerInfo("Alex", alex))));
        roster.update("a", new C2SPlayerJoin(2, "Alex", alex, 1));
        assertEquals(Collections.singletonList(alex),
                roster.entry("a").getPlayers().stream().map(PlayerInfo::getUuid).collect(Collectors.toList()));
    }

    private static C2SHandshake handshake(String id, ServerState state) {
        return new C2SHandshake(1, id, "Server " + id, "token", 1, state, "#ffffff", id + ".example.org", 20, 0, 0);
    }

    private static long seconds(long seconds) {
        return TimeUnit.SECONDS.toNanos(seconds);
    }
}
