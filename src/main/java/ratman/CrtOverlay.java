package ratman;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Optional old-TV look (Settings → CRT), drawn on the window's glass pane so it covers every
 * screen: scanlines, darker rounded corners, a vignette, a faint flicker and a slow rolling band.
 * The static part is drawn once into an image and reused, so it costs almost nothing per frame.
 * It has no mouse listeners, so clicks go straight through to the buttons underneath.
 */
final class CrtOverlay extends JComponent {
    private static final int SCANLINE_PERIOD = 3;     // one dark line every 3 pixels
    private static final int SCANLINE_ALPHA = 70;
    private static final int CORNER_RADIUS = 46;

    private BufferedImage cached;
    private final long start = System.nanoTime();
    private boolean wasOn;

    CrtOverlay() {
        setOpaque(false);
        // keep the flicker and rolling band moving, even on menus that don't repaint on their own
        new javax.swing.Timer(33, e -> {
            boolean on = Settings.crtOn();
            if (on || wasOn) repaint();
            wasOn = on;
        }).start();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (!Settings.crtOn()) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (cached == null || cached.getWidth() != w || cached.getHeight() != h) cached = build(w, h);

        Graphics2D g = (Graphics2D) graphics.create();
        g.drawImage(cached, 0, 0, null);

        double t = (System.nanoTime() - start) / 1e9;
        // slow bright band rolling down the screen, like an old TV
        int bandH = Math.max(40, h / 8);
        int bandY = (int) ((t * 45) % (h + bandH)) - bandH;
        g.setPaint(new GradientPaint(0, bandY, new Color(255, 255, 255, 0),
                0, bandY + bandH / 2f, new Color(255, 255, 255, 14), true));
        g.fillRect(0, bandY, w, bandH);
        // faint flicker
        int flicker = (int) (6 + 5 * Math.sin(t * 37) * Math.sin(t * 11.3));
        g.setColor(new Color(0, 0, 0, Math.max(0, flicker)));
        g.fillRect(0, 0, w, h);
        g.dispose();
    }

    private static BufferedImage build(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // scanlines
        g.setColor(new Color(0, 0, 0, SCANLINE_ALPHA));
        for (int y = 0; y < h; y += SCANLINE_PERIOD) g.fillRect(0, y, w, 1);

        // faint vertical RGB stripes (the phosphor mask)
        Color[] mask = {new Color(255, 0, 0, 7), new Color(0, 255, 0, 7), new Color(0, 0, 255, 7)};
        for (int x = 0; x < w; x++) {
            g.setColor(mask[x % 3]);
            g.fillRect(x, 0, 1, h);
        }

        // vignette: edges darker than the middle
        float radius = (float) Math.hypot(w, h) / 2f;
        g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h / 2f), radius,
                new float[]{0f, 0.6f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 30), new Color(0, 0, 0, 150)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fillRect(0, 0, w, h);

        // rounded "curved glass" corners
        Area outside = new Area(new Rectangle2D.Float(0, 0, w, h));
        outside.subtract(new Area(new RoundRectangle2D.Float(0, 0, w, h, CORNER_RADIUS * 2, CORNER_RADIUS * 2)));
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(Color.BLACK);
        g.fill(outside);
        g.dispose();
        return img;
    }
}
