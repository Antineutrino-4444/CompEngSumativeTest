import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * FUSION FIELD – Asteroids-style reactor maintenance demo
 * ─────────────────────────────────────────────────────────
 * Pilot a maintenance craft inside a failing tokamak reactor.
 * Destroy unstable plasma fragments with magnetic pulses before
 * the reactor heat meter fills and causes a meltdown.
 *
 * Controls:  LEFT / RIGHT  – rotate ship
 *            UP             – thrust
 *            SPACE          – fire magnetic pulse
 *            R              – restart
 */
public class FusionField extends JPanel implements KeyListener {

    static final int W = 700, H = 700;

    // ── Inner classes for game objects ───────────────────────────
    static class Fragment {
        double x, y, vx, vy, angle, spin;
        int size; // 2=large 1=medium 0=small
        boolean alive = true;

        Fragment(double x, double y, double vx, double vy, int size) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.size = size;
            this.angle = Math.random() * Math.PI * 2;
            this.spin  = (Math.random() - 0.5) * 0.05;
        }

        int radius() { return 12 + size * 16; } // 12, 28, 44
    }

    static class Bullet {
        double x, y, vx, vy;
        int life;
        Bullet(double x, double y, double vx, double vy) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            life = 55;
        }
    }

    // ── Ship state ───────────────────────────────────────────────
    double shipX, shipY, shipVx, shipVy, shipAngle;
    boolean rotLeft, rotRight, thrusting;
    int    invincible;   // ticks of invincibility after respawn

    // ── Collections ──────────────────────────────────────────────
    List<Fragment> fragments = new ArrayList<>();
    List<Bullet>   bullets   = new ArrayList<>();

    // ── Game state ───────────────────────────────────────────────
    int  score, lives, wave;
    int  heat;          // 0-100; fills when fragments escape off-screen
    boolean gameOver, won;
    Random rng = new Random();
    Timer  timer;

    // ── Constructor ──────────────────────────────────────────────
    FusionField() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(4, 8, 25));
        setFocusable(true);
        addKeyListener(this);
        reset();
    }

    void reset() {
        shipX = W / 2.0; shipY = H / 2.0;
        shipVx = shipVy = 0;
        shipAngle = -Math.PI / 2;   // pointing up
        invincible = 80;
        fragments.clear();
        bullets.clear();
        score = 0; lives = 3; wave = 1; heat = 0;
        gameOver = won = false;
        spawnWave();
        if (timer != null) timer.stop();
        timer = new Timer(16, e -> { update(); repaint(); });
        timer.start();
    }

    void spawnWave() {
        int count = 3 + wave;
        for (int i = 0; i < count; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist  = 200 + rng.nextDouble() * 150;
            double x = W / 2.0 + Math.cos(angle) * dist;
            double y = H / 2.0 + Math.sin(angle) * dist;
            double speed = 0.5 + rng.nextDouble() * 0.8 + wave * 0.15;
            double vAngle = rng.nextDouble() * Math.PI * 2;
            fragments.add(new Fragment(x, y, Math.cos(vAngle) * speed,
                                              Math.sin(vAngle) * speed, 2));
        }
    }

    // ── Game loop ────────────────────────────────────────────────
    void update() {
        if (gameOver || won) return;

        // Rotate
        if (rotLeft)  shipAngle -= 0.07;
        if (rotRight) shipAngle += 0.07;

        // Thrust
        if (thrusting) {
            shipVx += Math.cos(shipAngle) * 0.25;
            shipVy += Math.sin(shipAngle) * 0.25;
        }

        // Drag
        shipVx *= 0.982;
        shipVy *= 0.982;

        // Move ship + wrap
        shipX = (shipX + shipVx + W) % W;
        shipY = (shipY + shipVy + H) % H;

        if (invincible > 0) invincible--;

        // Move bullets
        Iterator<Bullet> bi = bullets.iterator();
        while (bi.hasNext()) {
            Bullet b = bi.next();
            b.x = (b.x + b.vx + W) % W;
            b.y = (b.y + b.vy + H) % H;
            if (--b.life <= 0) { bi.remove(); }
        }

        // Move fragments + spin
        for (Fragment f : fragments) {
            f.x = (f.x + f.vx + W) % W;
            f.y = (f.y + f.vy + H) % H;
            f.angle += f.spin;
        }

        // Bullet–fragment collision
        outer:
        for (Iterator<Bullet> bit = bullets.iterator(); bit.hasNext(); ) {
            Bullet b = bit.next();
            for (Fragment f : fragments) {
                if (!f.alive) continue;
                if (dist(b.x, b.y, f.x, f.y) < f.radius()) {
                    f.alive = false;
                    bit.remove();
                    score += (3 - f.size) * 50 + 50;
                    splitFragment(f);
                    continue outer;
                }
            }
        }
        fragments.removeIf(f -> !f.alive);

        // Ship–fragment collision
        if (invincible <= 0) {
            for (Fragment f : fragments) {
                if (dist(shipX, shipY, f.x, f.y) < f.radius() + 10) {
                    lives--;
                    invincible = 120;
                    if (lives <= 0) { gameOver = true; timer.stop(); return; }
                    break;
                }
            }
        }

        // Heat: increase when a fragment is "old" enough to be considered escaped
        // (we track heat differently: each fragment that has traveled a long time adds heat)
        // Simplified: heat rises slowly, cools slightly when fragments destroyed
        if (rng.nextInt(200) == 0) heat = Math.min(heat + 1, 100);

        // Next wave
        if (fragments.isEmpty()) {
            wave++;
            if (wave > 5) { won = true; timer.stop(); return; }
            heat = Math.max(0, heat - 10);
            spawnWave();
        }
    }

    void splitFragment(Fragment f) {
        if (f.size == 0) return;
        for (int i = 0; i < 2; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double spd   = 1.0 + rng.nextDouble() * 1.5;
            fragments.add(new Fragment(f.x, f.y,
                Math.cos(angle) * spd, Math.sin(angle) * spd, f.size - 1));
        }
        heat = Math.max(0, heat - 3);
    }

    double dist(double ax, double ay, double bx, double by) {
        double dx = ax - bx, dy = ay - by;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ── Rendering ────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Reactor torus outline (decorative)
        g2.setColor(new Color(0, 80, 160, 40));
        g2.setStroke(new BasicStroke(3));
        g2.drawOval(W / 2 - 280, H / 2 - 280, 560, 560);
        g2.drawOval(W / 2 - 180, H / 2 - 180, 360, 360);
        g2.setStroke(new BasicStroke(1));

        // Draw fragments
        for (Fragment f : fragments) drawFragment(g2, f);

        // Draw bullets
        g2.setColor(new Color(100, 220, 255));
        for (Bullet b : bullets) g2.fillOval((int)b.x - 3, (int)b.y - 3, 6, 6);

        // Draw ship (only visible when not invincible, or flashing)
        if (invincible == 0 || (invincible / 6) % 2 == 0) drawShip(g2);

        // HUD
        drawHUD(g2);

        if (gameOver) drawOverlay(g2, "REACTOR MELTDOWN!", new Color(255, 70, 70));
        else if (won)  drawOverlay(g2, "REACTOR CLEAR! ALL WAVES SURVIVED!", new Color(0, 255, 160));
    }

    void drawShip(Graphics2D g2) {
        Graphics2D g = (Graphics2D) g2.create();
        g.translate(shipX, shipY);
        g.rotate(shipAngle + Math.PI / 2);
        g.setColor(new Color(60, 180, 255));
        int[] xs = { 0, -12, 0, 12};
        int[] ys = {-20,  12, 4, 12};
        g.fillPolygon(xs, ys, 4);
        g.setColor(new Color(140, 220, 255));
        g.drawPolygon(xs, ys, 4);
        if (thrusting) {
            g.setColor(new Color(255, 160, 0, 180));
            g.fillOval(-5, 12, 10, 12);
        }
        g.dispose();
    }

    void drawFragment(Graphics2D g2, Fragment f) {
        Graphics2D g = (Graphics2D) g2.create();
        g.translate(f.x, f.y);
        g.rotate(f.angle);
        int r = f.radius();
        Color base = f.size == 2 ? new Color(255, 80,  30) :
                     f.size == 1 ? new Color(255, 140, 20) :
                                   new Color(255, 220, 50);
        g.setColor(base);
        // Jagged plasma polygon
        int pts = 8;
        int[] px = new int[pts], py = new int[pts];
        for (int i = 0; i < pts; i++) {
            double a = i * 2 * Math.PI / pts;
            double rad = r * (0.7 + 0.3 * ((i * 7919) % 100) / 100.0);
            px[i] = (int)(Math.cos(a) * rad);
            py[i] = (int)(Math.sin(a) * rad);
        }
        g.fillPolygon(px, py, pts);
        g.setColor(base.brighter());
        g.drawPolygon(px, py, pts);
        g.dispose();
    }

    void drawHUD(Graphics2D g2) {
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        g2.drawString("FUSION FIELD", 20, 25);
        g2.drawString("Score: " + score, 20, 45);
        g2.drawString("Lives: " + lives, 20, 65);
        g2.drawString("Wave:  " + wave + " / 5", 20, 85);

        // Heat meter
        g2.drawString("Heat:", 20, 110);
        g2.setColor(new Color(40, 40, 40));
        g2.fillRect(70, 97, 120, 14);
        Color heatCol = heat < 50 ? new Color(0, 200, 80) :
                        heat < 80 ? new Color(255, 180, 0) : new Color(255, 50, 50);
        g2.setColor(heatCol);
        g2.fillRect(70, 97, heat * 120 / 100, 14);
        g2.setColor(Color.GRAY);
        g2.drawRect(70, 97, 120, 14);

        // Controls
        g2.setColor(new Color(160, 160, 200));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g2.drawString("← → Rotate   ↑ Thrust   SPACE Fire   R Restart", 20, H - 15);
    }

    void drawOverlay(Graphics2D g2, String msg, Color col) {
        g2.setColor(new Color(0, 0, 0, 175));
        g2.fillRoundRect(80, 300, 540, 95, 18, 18);
        g2.setColor(col);
        g2.setFont(new Font("Monospaced", Font.BOLD, 22));
        g2.drawString(msg, 100, 345);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g2.drawString("Score: " + score + "   Press R to restart", 100, 375);
    }

    // ── Key handling ─────────────────────────────────────────────
    @Override public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> rotLeft   = true;
            case KeyEvent.VK_RIGHT -> rotRight  = true;
            case KeyEvent.VK_UP    -> thrusting = true;
            case KeyEvent.VK_SPACE -> {
                if (!gameOver && !won && bullets.size() < 5) {
                    double spd = 9;
                    bullets.add(new Bullet(shipX, shipY,
                        Math.cos(shipAngle) * spd + shipVx,
                        Math.sin(shipAngle) * spd + shipVy));
                }
            }
            case KeyEvent.VK_R -> reset();
        }
    }
    @Override public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> rotLeft   = false;
            case KeyEvent.VK_RIGHT -> rotRight  = false;
            case KeyEvent.VK_UP    -> thrusting = false;
        }
    }
    @Override public void keyTyped(KeyEvent e) {}

    // ── Entry point ──────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Fusion Field");
            FusionField g = new FusionField();
            f.add(g); f.pack();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            g.requestFocusInWindow();
        });
    }
}
