package ratman;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Sound effects and background music. All audio is original chiptune made by tools/make_audio.py.
 * Each sound keeps one open Clip that is rewound and replayed, instead of opening a new audio
 * line every time (which is slow and leaks lines).
 */
public final class SoundPlayer {

    public enum Sound {
        MUNCH_A("sfx_munch_a.wav"),
        MUNCH_B("sfx_munch_b.wav"),
        CHEESE("sfx_cheese.wav"),
        EAT_CAT("sfx_eat_cat.wav"),
        DEATH("sfx_death.wav"),
        PAUSE("sfx_pause.wav"),
        CLICK("sfx_click.wav"),
        CAT_FREE("sfx_cat_free.wav"),
        START("jingle_start.wav"),
        LEVEL_CLEAR("jingle_level_clear.wav"),
        GAME_OVER("jingle_game_over.wav"),
        POP("sfx_pop.wav"),
        BOSS_WARNING("sfx_boss_warning.wav"),
        BOSS_ROAR("sfx_boss_roar.wav"),
        BOSS_WINDUP("sfx_boss_windup.wav"),
        BOSS_DASH("sfx_boss_dash.wav"),
        BOSS_THUD("sfx_boss_thud.wav"),
        BOSS_EXPLODE("sfx_boss_explode.wav"),
        BOSS_DEFEAT("jingle_boss_defeat.wav"),
        CAT_COMBO("jingle_cat_combo.wav");

        final String path;

        Sound(String file) {
            this.path = "/sounds/" + file;
        }
    }

    public enum Track {
        MENU("music_menu.wav"),
        LEVEL("music_level.wav"),
        BOSS("music_boss.wav");

        final String path;

        Track(String file) {
            this.path = "/sounds/" + file;
        }
    }

    private static final float SFX_DB = -3f;
    private static final float MUSIC_DB = -10f; // music sits under the effects

    private static boolean enabled = true;
    private static final Map<Sound, Clip> SFX = new EnumMap<>(Sound.class);
    private static final Map<Track, Clip> MUSIC = new EnumMap<>(Track.class);
    private static Track currentTrack;
    private static boolean musicPaused;
    private static boolean webMusicOn; // browser version: a track was actually started

    private SoundPlayer() {}

    /** Turns all audio off (used by tests / machines without audio). */
    public static void setEnabled(boolean on) {
        enabled = on;
        if (!on) stopMusic();
    }

    // ---- sound effects ----

    public static void play(Sound sound) {
        if (!enabled || !Settings.sfxOn()) return;
        if (RatMan.inBrowser) {
            web(() -> WebBridge.playSound(sound.path.substring(1), SFX_DB));
            return;
        }
        Clip clip = SFX.computeIfAbsent(sound, s -> open(s.path, SFX_DB));
        if (clip == null) return;
        clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    // ---- music ----

    /** Starts looping a track (does nothing if it's already playing). */
    public static void playMusic(Track track) {
        if (track == currentTrack && !musicPaused && isRunning(track)) return;
        stopMusic();
        currentTrack = track;
        musicPaused = false;
        if (!enabled || !Settings.musicOn()) return;
        if (RatMan.inBrowser) {
            web(() -> WebBridge.playMusic(track.path.substring(1), MUSIC_DB));
            webMusicOn = true;
            return;
        }
        Clip clip = MUSIC.computeIfAbsent(track, t -> open(t.path, MUSIC_DB));
        if (clip == null) return;
        clip.setFramePosition(0);
        clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public static void stopMusic() {
        if (RatMan.inBrowser && webMusicOn) web(WebBridge::stopMusic);
        webMusicOn = false;
        if (currentTrack != null) {
            Clip clip = MUSIC.get(currentTrack);
            if (clip != null) clip.stop();
        }
        currentTrack = null;
        musicPaused = false;
    }

    /** Pause keeps the playback position so resume carries on where it left off. */
    public static void pauseMusic() {
        if (RatMan.inBrowser) {
            if (webMusicOn && !musicPaused) {
                web(WebBridge::pauseMusic);
                musicPaused = true;
            }
            return;
        }
        Clip clip = currentTrack == null ? null : MUSIC.get(currentTrack);
        if (clip != null && clip.isRunning()) {
            clip.stop();
            musicPaused = true;
        }
    }

    public static void resumeMusic() {
        if (RatMan.inBrowser) {
            if (webMusicOn && musicPaused && enabled && Settings.musicOn()) web(WebBridge::resumeMusic);
            musicPaused = false;
            return;
        }
        Clip clip = currentTrack == null ? null : MUSIC.get(currentTrack);
        if (clip != null && musicPaused && enabled && Settings.musicOn()) {
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        }
        musicPaused = false;
    }

    /** Called when the Music setting changes. */
    public static void musicSettingChanged(Track trackIfOn) {
        if (Settings.musicOn()) playMusic(trackIfOn);
        else stopMusic();
    }

    /** Browser version: hand audio to the web page; if that fails, carry on silently. */
    private static void web(Runnable call) {
        try {
            call.run();
        } catch (Throwable t) {
            System.err.println("Browser audio unavailable: " + t);
            enabled = false;
        }
    }

    private static boolean isRunning(Track track) {
        if (RatMan.inBrowser) return webMusicOn && track == currentTrack && !musicPaused;
        Clip clip = MUSIC.get(track);
        return clip != null && clip.isRunning();
    }

    private static Clip open(String path, float gainDb) {
        try {
            byte[] data = Assets.bytes(path);
            AudioInputStream audio = AudioSystem.getAudioInputStream(
                    new BufferedInputStream(new ByteArrayInputStream(data)));
            Clip clip = AudioSystem.getClip();
            clip.open(audio);
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), gainDb)));
            }
            return clip;
        } catch (Exception e) {
            // No audio device etc. — keep the game running silently.
            System.err.println("Audio unavailable (" + path + "): " + e.getMessage() + ". Continuing without sound.");
            enabled = false; // don't retry (and re-print) on every sound
            return null;
        }
    }
}
