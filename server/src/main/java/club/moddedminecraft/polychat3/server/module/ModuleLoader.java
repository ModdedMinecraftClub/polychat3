package club.moddedminecraft.polychat3.server.module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import club.moddedminecraft.polychat3.server.hub.Hub;

/** Finds the modules config names, and starts and stops them. */
public final class ModuleLoader {
    private static final Logger LOG = LoggerFactory.getLogger(ModuleLoader.class);

    private ModuleLoader() {
    }

    /**
     * The modules config names, in the order it names them.
     *
     * @throws IllegalArgumentException if a name matches no module, or is listed twice.
     */
    static List<Module> select(Iterable<Module> available, List<String> names) {
        List<Module> selected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                throw new IllegalArgumentException("Duplicate module name " + name);
            }
            boolean found = false;
            for (Module module : available) {
                if (module.id().equals(name)) {
                    selected.add(module);
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("No module named " + name);
            }
        }
        return selected;
    }

    /** Every module in the jar, as META-INF/services lists them, picked by name as select does. */
    public static List<Module> load(List<String> names) {
        return select(ServiceLoader.load(Module.class), names);
    }

    /**
     * Enables each module in order, each with its own context. Stops at the first one that throws.
     *
     * @throws Exception naming the module that failed, with its own exception as the cause.
     */
    public static void enable(List<Module> modules, Hub hub) throws Exception {
        for (Module module : modules) {
            LOG.info("Enabling module {}", module.id());
            try {
                module.enable(new ModuleContext(hub, module.id()));
            } catch (Exception e) {
                throw new Exception("Module " + module.id() + " failed to start: " + e.getMessage(), e);
            }
        }
    }

    /** Disables the modules in reverse order. One that throws is logged, and the rest still stop. */
    public static void disable(List<Module> modules) {
        for (int i = modules.size() - 1; i >= 0; i--) {
            Module module = modules.get(i);
            try {
                module.disable();
            } catch (RuntimeException e) {
                LOG.error("Module {} failed to stop", module.id(), e);
            }
        }
    }
}
