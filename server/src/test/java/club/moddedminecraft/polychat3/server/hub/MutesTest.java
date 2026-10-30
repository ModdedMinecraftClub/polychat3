package club.moddedminecraft.polychat3.server.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MutesTest {
    private static final String A = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String B = "ec561538-f3fd-461d-aff5-086b22154bce";

    @TempDir
    Path dir;

    @Test
    void missingFileMeansNobodyIsMuted() throws IOException {
        Mutes mutes = new Mutes(dir.resolve("mutes.json"));
        mutes.load();
        assertEquals(Collections.emptyList(), mutes.list());
        assertFalse(Files.exists(dir.resolve("mutes.json")), "load must not create the file");
    }

    @Test
    void loadsAndNormalisesEntries() throws IOException {
        Mutes mutes = load("[\"" + B + "\", \"" + A.toUpperCase() + "\"]");
        assertEquals(Arrays.asList(A, B), mutes.list());
    }

    @Test
    void loadRefusesBrokenFiles() throws IOException {
        // Each would otherwise start the hub with an empty list, and the next save would wipe every mute
        for (String content : new String[] {"", "   ", "{", "{\"muted\": []}", "42", "[null]", "[\"nope\"]",
                "[\"1-2-3-4-5\"]", "[\"" + A + "\"] junk"}) {
            Path file = write(content);
            assertThrows(IOException.class, () -> new Mutes(file).load(), "content: " + content);
        }
    }

    @Test
    void setReturnsTrueOnlyWhenTheListChanges() throws IOException {
        Mutes mutes = load("[]");
        assertTrue(mutes.set(A, true));
        assertFalse(mutes.set(A, true), "already muted");
        assertFalse(mutes.set(A.toUpperCase(), true), "same player in uppercase");
        assertTrue(mutes.set(A, false));
        assertFalse(mutes.set(A, false), "already unmuted");
        assertFalse(mutes.set(B, false), "never muted");
    }

    @Test
    void setRefusesATargetThatIsNotAUuid() throws IOException {
        Mutes mutes = load("[]");
        assertThrows(IllegalArgumentException.class, () -> mutes.set("nope", true));
        assertThrows(IllegalArgumentException.class, () -> mutes.set("1-2-3-4-5", true));
        assertEquals(Collections.emptyList(), mutes.list());
    }

    @Test
    void changesAreSavedAndLoadAgain() throws IOException {
        Path file = dir.resolve("mutes.json");
        Mutes mutes = new Mutes(file);
        mutes.load();
        mutes.set(B.toUpperCase(), true);
        mutes.set(A, true);
        mutes.set(B, false);
        mutes.set(B, true);

        Mutes reloaded = new Mutes(file);
        reloaded.load();
        assertEquals(Arrays.asList(A, B), reloaded.list());
        assertFalse(Files.exists(dir.resolve("mutes.json.tmp")), "the temp file is moved, not left behind");
        String saved = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertFalse(saved.contains(B.toUpperCase()), "saved normalised: " + saved);
    }

    @Test
    void unmutingSavesToo() throws IOException {
        Path file = dir.resolve("mutes.json");
        Mutes mutes = new Mutes(file);
        mutes.set(A, true);
        mutes.set(A, false);

        Mutes reloaded = new Mutes(file);
        reloaded.load();
        assertEquals(Collections.emptyList(), reloaded.list());
    }

    @Test
    void isMutedIgnoresCaseAndRejectsNonUuids() throws IOException {
        Mutes mutes = load("[\"" + A + "\"]");
        assertTrue(mutes.isMuted(A));
        assertTrue(mutes.isMuted(A.toUpperCase()));
        assertFalse(mutes.isMuted(B));
        assertFalse(mutes.isMuted("nope"));
        assertFalse(mutes.isMuted("1-2-3-4-5"));
    }

    @Test
    void listIsASortedCopy() throws IOException {
        Mutes mutes = load("[]");
        mutes.set(B, true);
        mutes.set(A, true);
        List<String> list = mutes.list();
        assertEquals(Arrays.asList(A, B), list);
        list.clear();
        assertEquals(Arrays.asList(A, B), mutes.list(), "changing the copy changes nothing");
    }

    @Test
    void normaliseLowercasesAndRefusesShortForms() {
        assertEquals(A, Mutes.normalise(A.toUpperCase()));
        assertEquals(A, Mutes.normalise(A));
        assertThrows(IllegalArgumentException.class, () -> Mutes.normalise("1-2-3-4-5"));
        assertThrows(IllegalArgumentException.class, () -> Mutes.normalise(A.replace("-", "")));
        assertThrows(IllegalArgumentException.class, () -> Mutes.normalise(""));
    }

    private Path write(String content) throws IOException {
        Path file = Files.createTempFile(dir, "mutes", ".json");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private Mutes load(String content) throws IOException {
        Mutes mutes = new Mutes(write(content));
        mutes.load();
        return mutes;
    }
}
