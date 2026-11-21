package ratman;

import com.studiohartman.jamepad.ControllerAxis;
import com.studiohartman.jamepad.ControllerButton;

/**
 * Browser version only (RatMan.inBrowser): sound and controllers go through the web page.
 * The native methods below are written in JavaScript in web/bridge.js and handed to CheerpJ,
 * which runs this Java code in the browser. Never called by the desktop apps.
 */
final class WebBridge {
    private WebBridge() {}

    // ---- sound: played with the browser's Web Audio (paths like "sounds/sfx_pop.wav") ----
    static native void playSound(String path, float gainDb);

    static native void playMusic(String path, float gainDb);

    static native void stopMusic();

    static native void pauseMusic();

    static native void resumeMusic();

    // ---- controller: the browser's Gamepad API ----
    /** Fills out: [0] 1 if a controller is connected, [1] pressed-buttons bitmask, [2] stick x, [3] stick y. */
    static native void padState(float[] out);

    static native String padName();

    static native void padRumble(float strong, float weak, int millis);

    /** Controller readings from the browser (standard button layout, the same on PS5, Xbox, Switch Pro). */
    static final class PadSource implements Gamepad.Source {
        private static final long REFRESH_NANOS = 8_000_000L; // read at most every 8 ms
        private final float[] state = new float[4];
        private long lastRead;
        private boolean wasConnected;
        private String name = "";

        private void refresh() {
            long now = System.nanoTime();
            if (now - lastRead < REFRESH_NANOS) return;
            lastRead = now;
            padState(state);
            boolean on = state[0] > 0.5f;
            if (on && !wasConnected) {
                name = padName();
                System.out.println("Controller connected: " + name);
            }
            wasConnected = on;
        }

        @Override
        public boolean connected() {
            refresh();
            return wasConnected;
        }

        @Override
        public boolean pressed(ControllerButton b) {
            int bit = switch (b) {          // the browser's "standard gamepad" button numbers
                case A -> 0;                 // Cross
                case B -> 1;                 // Circle
                case X -> 2;
                case Y -> 3;
                case BACK -> 8;
                case START -> 9;             // Options
                case DPAD_UP -> 12;
                case DPAD_DOWN -> 13;
                case DPAD_LEFT -> 14;
                case DPAD_RIGHT -> 15;
                default -> -1;
            };
            return bit >= 0 && (((int) state[1]) & (1 << bit)) != 0;
        }

        @Override
        public float axis(ControllerAxis a) {
            return switch (a) {
                case LEFTX -> state[2];
                case LEFTY -> state[3];       // up is negative, same as the desktop library
                default -> 0f;
            };
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void rumble(float left, float right, int millis) {
            padRumble(left, right, millis);
        }
    }
}
