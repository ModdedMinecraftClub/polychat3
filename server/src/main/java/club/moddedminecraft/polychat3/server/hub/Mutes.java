package club.moddedminecraft.polychat3.server.hub;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The network's mute list, as in PROTOCOL.md "Mutes": Minecraft UUIDs, saved to a JSON file on every change so it
 * survives hub restarts. Only used on the hub thread.
 */
final class Mutes {
    private static final Logger LOG = LoggerFactory.getLogger(Mutes.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    /** Lowercase dashed UUIDs, sorted so the file stays stable. */
    private final Set<String> muted = new TreeSet<>();

    Mutes(Path file) {
        this.file = file;
    }

    /**
     * Reads the file. A missing file means nobody is muted.
     *
     * @throws IOException if the file can't be read or isn't a JSON array of UUIDs.
     */
    void load() throws IOException {
        if (Files.notExists(file)) {
            return;
        }
        String[] entries;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            entries = GSON.fromJson(reader, String[].class);
        } catch (JsonParseException e) {
            throw new IOException(file + " is not valid JSON: " + e.getMessage(), e);
        }
        if (entries == null) {
            throw new IOException(file + " is empty");
        }
        for (String entry : entries) {
            // Gson reads [null] as a null element, which normalise can't take
            if (entry == null) {
                throw new IOException(file + " has null in its list");
            }
            try {
                muted.add(normalise(entry));
            } catch (IllegalArgumentException e) {
                throw new IOException(file + " has " + entry + ", which is not a UUID", e);
            }
        }
    }

    /**
     * Mutes or unmutes target, and saves the list if that changed it.
     *
     * @return true if the list changed.
     * @throws IllegalArgumentException if target isn't a UUID.
     */
    boolean set(String target, boolean mute) {
        String uuid = normalise(target);
        boolean changed = mute ? muted.add(uuid) : muted.remove(uuid);
        if (changed) {
            try {
                save();
            } catch (IOException e) {
                LOG.error("Couldn't save {}; the change is lost when the hub restarts", file, e);
            }
        }
        return changed;
    }

    /** @return true if uuid is muted, false if it isn't or isn't a UUID. */
    boolean isMuted(String uuid) {
        try {
            return muted.contains(normalise(uuid));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** @return every muted UUID, sorted. A copy. */
    List<String> list() {
        return new ArrayList<>(muted);
    }

    private void save() throws IOException {
        // Written next to the file and then moved over it, so a crash mid-write never leaves half a file
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, GSON.toJson(muted).getBytes(StandardCharsets.UTF_8));
        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /** @throws IllegalArgumentException if uuid isn't a dashed UUID. */
    static String normalise(String uuid) {
        String normalised = UUID.fromString(uuid).toString();
        if (!normalised.equalsIgnoreCase(uuid)) {
            throw new IllegalArgumentException("not a dashed UUID: " + uuid);
        }
        return normalised;
    }
}