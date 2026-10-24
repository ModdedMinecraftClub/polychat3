package club.moddedminecraft.polychat3.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;

public final class ConfigLoader {
    private static final Gson GSON = new GsonBuilder()
            .setStrictness(Strictness.LENIENT) // allows // and /* */ comments
            .create();

    public static <T> T load(Path file, Class<T> type, String defaultResource) throws IOException {
        if (Files.notExists(file)) {
            try (InputStream in = ConfigLoader.class.getResourceAsStream(defaultResource)) {
                if (in == null) throw new IllegalStateException("missing default " + defaultResource);
                Files.createDirectories(file.toAbsolutePath().getParent());
                Files.copy(in, file);
            }
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, type);
        }
    }
}