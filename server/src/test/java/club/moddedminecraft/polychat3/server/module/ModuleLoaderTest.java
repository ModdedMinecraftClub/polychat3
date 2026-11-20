package club.moddedminecraft.polychat3.server.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Picking the modules config names, with fake modules instead of ServiceLoader. */
class ModuleLoaderTest {
    private final List<Module> available = Arrays.asList(new Fake("console"), new Fake("broadcasts"),
            new Fake("discord"));

    @Test
    void picksTheNamedModulesInConfigOrder() {
        List<Module> chosen = ModuleLoader.select(available, Arrays.asList("discord", "console"));
        assertEquals(Arrays.asList("discord", "console"), ids(chosen));
    }

    /** A module that isn't first among the available ones is still found. */
    @Test
    void findsAModuleAnywhereInTheAvailableList() {
        assertEquals(Arrays.asList("broadcasts"), ids(ModuleLoader.select(available, Arrays.asList("broadcasts"))));
    }

    @Test
    void noNamesPicksNothing() {
        assertTrue(ModuleLoader.select(available, Collections.emptyList()).isEmpty());
    }

    @Test
    void refusesAnUnknownName() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ModuleLoader.select(available, Arrays.asList("console", "dicsord")));
        assertTrue(e.getMessage().contains("dicsord"), e.getMessage());
    }

    @Test
    void refusesANameListedTwice() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ModuleLoader.select(available, Arrays.asList("broadcasts", "console", "broadcasts")));
        assertTrue(e.getMessage().contains("broadcasts"), e.getMessage());
    }

    @Test
    void enablesInOrderAndDisablesInReverse() throws Exception {
        List<String> calls = new ArrayList<>();
        List<Module> modules = Arrays.asList(new Recording("a", calls, false), new Recording("b", calls, false));
        ModuleLoader.enable(modules, null);
        ModuleLoader.disable(modules);
        assertEquals(Arrays.asList("enable a", "enable b", "disable b", "disable a"), calls);
    }

    /** The first module that fails stops the rest from starting, and the message names it. */
    @Test
    void enableStopsAtAFailureAndNamesTheModule() {
        List<String> calls = new ArrayList<>();
        List<Module> modules = Arrays.asList(new Recording("a", calls, true), new Recording("b", calls, false));
        Exception e = assertThrows(Exception.class, () -> ModuleLoader.enable(modules, null));
        assertTrue(e.getMessage().startsWith("Module a failed to start"), e.getMessage());
        assertEquals(Arrays.asList("enable a"), calls);
    }

    /** One module failing to stop doesn't keep the others running. */
    @Test
    void disableCarriesOnPastAFailure() {
        List<String> calls = new ArrayList<>();
        List<Module> modules = Arrays.asList(new Recording("a", calls, false), new Recording("b", calls, true));
        ModuleLoader.disable(modules);
        assertEquals(Arrays.asList("disable b", "disable a"), calls);
    }

    private static List<String> ids(List<Module> modules) {
        String[] ids = new String[modules.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = modules.get(i).id();
        }
        return Arrays.asList(ids);
    }

    /** A module that records its calls, and can be told to throw from both. */
    private static final class Recording implements Module {
        private final String id;
        private final List<String> calls;
        private final boolean fail;

        Recording(String id, List<String> calls, boolean fail) {
            this.id = id;
            this.calls = calls;
            this.fail = fail;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void enable(ModuleContext context) {
            calls.add("enable " + id);
            if (fail) {
                throw new IllegalStateException("broken");
            }
        }

        @Override
        public void disable() {
            calls.add("disable " + id);
            if (fail) {
                throw new IllegalStateException("broken");
            }
        }
    }

    /** A module that only has a name. */
    private static final class Fake implements Module {
        private final String id;

        Fake(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void enable(ModuleContext context) {
        }
    }
}
