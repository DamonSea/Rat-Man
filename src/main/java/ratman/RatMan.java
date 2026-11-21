package ratman;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Entry point: owns the window and swaps between the menu, skins and game screens. */
public final class RatMan {
    private static JFrame frame;
    private static GamePanel game;
    /** True when running in a web page (CheerpJ), started with the --browser argument. */
    static boolean inBrowser;

    private RatMan() {}

    public static void main(String[] args) {
        for (String a : args) {
            if (a.equals("--browser")) inBrowser = true;
        }
        if (inBrowser) {
            // CheerpJ keeps files under /files/ in the browser's storage between visits
            System.setProperty("ratman.saveDir", "/files/ratman");
        }
        useSameLookOnEveryOs();
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Rat-Man");
            setAppIcon(frame);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            if (inBrowser) frame.setUndecorated(true); // the web page is the window
            frame.setGlassPane(new CrtOverlay());       // optional CRT effect over every screen
            frame.getGlassPane().setVisible(true);
            // Controller support: start SDL once, then poll ~60x a second and hand the
            // actions to whichever screen is showing.
            // (In the browser the desktop controller library can't load; the web page reads controllers instead.)
            Gamepad.install(inBrowser ? new Gamepad(new WebBridge.PadSource()) : Gamepad.start());
            new javax.swing.Timer(16, e -> Gamepad.current().poll(
                    frame.getContentPane() instanceof Gamepad.Target t ? t : null)).start();

            showMenu();
            frame.setVisible(true);
        });
    }

    static void showMenu() {
        show(new Menu());
        SoundPlayer.playMusic(SoundPlayer.Track.MENU);
    }

    static void showSkins() {
        show(new SkinsMenu());
        SoundPlayer.playMusic(SoundPlayer.Track.MENU);
    }

    static void showHowToPlay() {
        show(new HowToPlayMenu());
        SoundPlayer.playMusic(SoundPlayer.Track.MENU);
    }

    static void showSettings() {
        show(new SettingsMenu());
        SoundPlayer.playMusic(SoundPlayer.Track.MENU);
    }

    static void startGame() {
        SoundPlayer.stopMusic();
        game = new GamePanel();
        show(game);
        game.start();
    }

    private static void show(JPanel panel) {
        if (game != null && panel != game) {
            game.stop();
            game = null;
        }
        frame.setContentPane(panel);
        frame.pack(); // size the window to the panel, so nothing is cut off by the title bar
        frame.setLocationRelativeTo(null); // re-centre, since the game screen is bigger than the menu
        frame.revalidate();
        frame.repaint();
    }

    /** Rat-on-cheese icon for the window and (on macOS) the Dock. */
    private static void setAppIcon(JFrame f) {
        java.awt.Image icon = Assets.image("/images/app_icon.png");
        f.setIconImage(icon);
        try {
            if (java.awt.Taskbar.isTaskbarSupported()
                    && java.awt.Taskbar.getTaskbar().isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                java.awt.Taskbar.getTaskbar().setIconImage(icon);
            }
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // not every OS lets apps change the taskbar icon
        }
    }

    /**
     * On macOS, Java's default button style ignores custom background colours, so the menu's
     * white button text was drawn on white buttons (blank START/SKINS). Using Java's own
     * cross-platform style makes the menus look the same on Mac and Windows.
     */
    private static void useSameLookOnEveryOs() {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Could not set look and feel: " + e.getMessage());
        }
    }

}
