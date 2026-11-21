package ratman;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Player settings, saved to settings.properties (see dataDir) so they're remembered between games. */
final class Settings {
    private static final Path FILE = dataDir().resolve("settings.properties");
    private static final Properties PROPS = new Properties();

    static {
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                PROPS.load(in);
            } catch (IOException e) {
                System.err.println("Could not read settings: " + e.getMessage());
            }
        }
    }

    private Settings() {}

    /** Where settings and scores are saved: ~/.ratman, or the browser's storage on the web. */
    static Path dataDir() {
        String dir = System.getProperty("ratman.saveDir");
        return dir != null ? Path.of(dir) : Path.of(System.getProperty("user.home"), ".ratman");
    }

    /** false = hand-designed levels (default), true = a new random maze every level. */
    static boolean randomMazes() {
        return Boolean.parseBoolean(PROPS.getProperty("randomMazes", "false"));
    }

    static void setRandomMazes(boolean on) {
        set("randomMazes", on);
    }

    /** Older versions had a single "sound" switch; it's the default for both new ones. */
    private static String legacySound() {
        return PROPS.getProperty("sound", "true");
    }

    static boolean musicOn() {
        return Boolean.parseBoolean(PROPS.getProperty("music", legacySound()));
    }

    static void setMusicOn(boolean on) {
        set("music", on);
    }

    static boolean sfxOn() {
        return Boolean.parseBoolean(PROPS.getProperty("sfx", legacySound()));
    }

    static void setSfxOn(boolean on) {
        set("sfx", on);
    }

    /** Controller vibration. */
    static boolean rumbleOn() {
        return Boolean.parseBoolean(PROPS.getProperty("rumble", "true"));
    }

    static void setRumbleOn(boolean on) {
        set("rumble", on);
    }

    /** Old-TV look over the whole window (off by default). */
    static boolean crtOn() {
        return Boolean.parseBoolean(PROPS.getProperty("crt", "false"));
    }

    static void setCrtOn(boolean on) {
        set("crt", on);
    }

    private static void set(String key, boolean value) {
        PROPS.setProperty(key, Boolean.toString(value));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                PROPS.store(out, "Rat-Man settings");
            }
        } catch (IOException e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }
}
