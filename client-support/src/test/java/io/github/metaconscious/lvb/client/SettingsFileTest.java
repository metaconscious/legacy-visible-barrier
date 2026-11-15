package io.github.metaconscious.lvb.client;

import io.github.metaconscious.lvb.core.RenderingPreferences;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.Assert.*;

public class SettingsFileTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void savesUtf8AndUnknownKeysWithoutChangingAlias() throws IOException {
        Path path = temporary.getRoot().toPath().resolve("lvb.properties");
        SettingsFile file = new SettingsFile(path);
        assertEquals(RenderingPreferences.DEFAULTS, file.read().preferences);
        assertFalse(Files.exists(path));
        Files.write(path, "renderMode=enhanced\nforeign=日本語\n".getBytes(StandardCharsets.UTF_8));
        SettingsFile.Snapshot initial = file.read();
        file.save(initial, new RenderingPreferences("enhanced", "reload"));
        assertEquals(new RenderingPreferences("enhanced", "reload"), file.read().preferences);
        assertTrue(new String(Files.readAllBytes(path), StandardCharsets.UTF_8).contains("foreign=日本語"));
    }

    @Test public void refusesExternalChangesAndWriteFailures() throws IOException {
        Path path = temporary.getRoot().toPath().resolve("lvb.properties");
        SettingsFile file = new SettingsFile(path);
        SettingsFile.Snapshot initial = file.read();
        byte[] external = "renderMode=off\n".getBytes(StandardCharsets.UTF_8);
        Files.write(path, external);
        try { file.save(initial, RenderingPreferences.DEFAULTS); fail("External edit overwritten"); }
        catch (SettingsFile.ChangedException expected) { assertArrayEquals(external, Files.readAllBytes(path)); }
        SettingsFile.Snapshot baseline = file.read();
        Files.delete(path);
        Files.createDirectory(path);
        try { file.save(baseline, RenderingPreferences.DEFAULTS); fail("Non-file target accepted"); }
        catch (IOException expected) { assertTrue(Files.isDirectory(path)); }
    }

    @Test public void invalidDocumentsArePreservedUntilExplicitRepair() throws IOException {
        Path path = temporary.getRoot().toPath().resolve("lvb.properties");
        SettingsFile file = new SettingsFile(path);
        byte[] invalid = { (byte) 0xc3, (byte) 0x28 };
        Files.write(path, invalid);
        SettingsFile.Snapshot original = file.read();
        assertFalse(original.valid);
        assertEquals(RenderingPreferences.SAFE, original.preferences);
        assertArrayEquals(invalid, Files.readAllBytes(path));
        file.save(original, RenderingPreferences.DEFAULTS);
        assertTrue(file.read().valid);
        try (Stream<Path> files = Files.list(path.getParent())) {
            Path backup = files.filter(p -> p.toString().endsWith(".bak")).findFirst().get();
            assertArrayEquals(invalid, Files.readAllBytes(backup));
        }
        Files.write(path, "renderMode=invalid\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(RenderingPreferences.SAFE, file.read().resolve("native", "dirty"));
    }
}
