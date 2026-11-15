package io.github.metaconscious.lvb.ornithe;

import io.github.metaconscious.lvb.core.RenderingPreferences;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Properties;

/** Instance-local persistence; never changes the running renderer. */
final class SettingsFile {
    private final Path path;

    SettingsFile(Path path) { this.path = path; }

    static final class Snapshot {
        private final byte[] bytes;
        private final Properties properties;
        private final boolean decoded;
        final RenderingPreferences preferences;
        final boolean valid;

        private Snapshot(byte[] bytes) {
            this.bytes = bytes;
            Properties parsed = new Properties();
            boolean readable = true;
            try {
                if (bytes != null) parsed.load(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()));
            } catch (IOException | IllegalArgumentException failure) {
                parsed.clear();
                readable = false;
            }
            properties = parsed;
            decoded = readable;
            RenderingPreferences value = RenderingPreferences.SAFE;
            boolean accepted = false;
            if (decoded) {
                try {
                    value = RenderingPreferences.resolve(properties, null, null);
                    accepted = true;
                } catch (IllegalArgumentException failure) { /* Retain the original for an explicit repair. */ }
            }
            preferences = value;
            valid = accepted;
        }

        RenderingPreferences resolve(String modeOverride, String refreshOverride) {
            if (!decoded) throw new IllegalArgumentException("Invalid LVB properties document");
            return RenderingPreferences.resolve(properties, modeOverride, refreshOverride);
        }
    }

    Snapshot read() throws IOException { return new Snapshot(readBytes()); }

    private byte[] readBytes() throws IOException {
        try { return Files.readAllBytes(path); }
        catch (NoSuchFileException missing) { return null; }
    }

    /** The only write operation. Unknown keys survive; invalid originals are backed up before repair. */
    Snapshot save(Snapshot baseline, RenderingPreferences preferences) throws IOException {
        requireUnchanged(baseline);
        Properties values = new Properties();
        values.putAll(baseline.properties);
        values.setProperty("renderMode", preferences.renderMode);
        values.setProperty("nativeRefresh", preferences.nativeRefresh);
        StringWriter writer = new StringWriter();
        values.store(writer, "Legacy Visible Barrier - rendering changes apply after restart");
        byte[] bytes = writer.toString().getBytes(StandardCharsets.UTF_8);
        Path directory = path.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "lvb-", ".tmp");
        try {
            Files.write(temporary, bytes);
            requireUnchanged(baseline);
            if (!baseline.valid && baseline.bytes != null) {
                Path backup = Files.createTempFile(directory, "lvb-recovery-", ".properties.bak");
                Files.write(backup, baseline.bytes);
            }
            // An unsupported atomic replacement is a failed save, never a delete-and-rewrite.
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return new Snapshot(bytes);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void requireUnchanged(Snapshot baseline) throws IOException {
        if (!Arrays.equals(baseline.bytes, readBytes())) throw new ChangedException();
    }

    static final class ChangedException extends IOException {
        private static final long serialVersionUID = 1L;
        ChangedException() { super("LVB settings changed on disk; reload before saving"); }
    }
}
