import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * FUSION SWARM – Galaga-style ion-swarm shooter demo
 * ───────────────────────────────────────────────────
 * Enemy ion swarms fly in formation inside a particle collider.
 * Some peel off to dive-bomb your maintenance craft. Destroy all
 * swarms before they overwhelm the containment system.
 *
 * Bonus: if an enemy captures your craft with a tractor beam,
 * destroy the captor to rescue the docked ship for double firepower.
 *
 * Controls:  LEFT / RIGHT – move craft
 *            SPACE         – fire
 *            R             – restart
 */
public class FusionSwarm extends JPanel implements KeyListener {

    static final int W = 620, H = 720;
    static final int E_ROWS = 4, E_COLS = 8;

    // ── Inner classes ─────────────────────────────────────────────
    enum EnemyState { FORMATION, DIVING, RETURNING }

    static class Enemy {
        double x, y;             // current position
        double homeX, homeY;     // formation slot
        EnemyState state;
        double vx, vy;
        boolean alive;
        int type;                // 0=drone 1=ion 2=boss
        int diveTimer;
        boolean hasCaptured;
        Enemy(double x, double y, int type) {
            this.x = homeX = x; this.y = homeY = y; this.type = type;
            state = EnemyState.FORMATION; alive = true;
        }
    }

    static class Bullet {
        double x, y, vy;
        boolean active;
        Bullet(double x, double y, double vy) { this.x = x; this.y = y; this.vy = vy; active = true; }
    }

    static class TractorBeam {
        double x, y, h;   // origin x/y, height (grows down)
        boolean active;
        Enemy owner;
        int timer;
        TractorBeam(Enemy owner) {
            this.owner = owner; this.x = owner.x; this.y = owner.y + 20;
            h = 0; active = true; timer = 90;
        }
    }

    // ── Game state ────────────────────────────────────────────────
    List<Enemy>      enemies     = new ArrayList<>();
    List<Bullet>     bullets     = new ArrayList<>();
    List<Bullet>     eBullets    = new ArrayList<>();
    List<TractorBeam> beams      = new ArrayList<>();

    double crafX, crafY;       // player craft position
    boolean moveLeft, moveRight;
    boolean dualShot;          // true when rescued captured craft is docked
    boolean capturedByBeam;    // player currently captured
    int     captureTimer;

    int score, lives, wave;
    boolean gameOver, won;
    double formOffX;           // formation oscillation offset
    int    formDir = 1;
    int    tick;
    Random rng = new Random();
    Timer  timer;

    FusionSwarm() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(2, 4, 20));
        setFocusable(true);
        addKeyListener(this);
        reset();
    }

    void reset() {
        score = 0; lives = 3; wave = 1;
        gameOver = won = false; dualShot = false;
        capturedByBeam = false; captureTimer = 0;
        crafX = W / 2.0; crafY = H - 70;
        spawnWave();
        if (timer != null) timer.stop();
        timer = new Timer(16, e -> { update(); repaint(); });
        timer.start();
    }

    void spawnWave() {
        enemies.clear(); bullets.clear(); eBullets.clear(); beams.clear(); tick = 0;
        double startX = 80, startY = 80, spacingX = 55, spacingY = 52;
        for (int r = 0; r < E_ROWS; r++) {
            for (int c = 0; c < E_COLS; c++) {
                int type = r == 0 ? 2 : r < 2 ? 1 : 0;
                double hx = startX + c * spacingX;
                double hy = startY + r * spacingY;
                enemies.add(new Enemy(hx, hy, type));
            }
        }
        formOffX = 0; formDir = 1;
    }

    // ── Update loop ───────────────────────────────────────────────
    void update() {
        if (gameOver || won) return;
        tick++;

        // Move craft
        if (!capturedByBeam) {
            if (moveLeft  && crafX > 24)    crafX -= 4;
            if (moveRight && crafX < W - 24) crafX += 4;
        }

        // Formation oscillation
        formOffX += formDir * 0.6;
        if (formOffX > 55 || formOffX < -55) formDir *= -1;

        // Update enemies
        updateEnemies();

        // Update tractor beams
        updateBeams();

        // Player bullets
        for (Iterator<Bullet> it = bullets.iterator(); it.hasNext(); ) {
            Bullet b = it.next();
            b.y += b.vy;
            if (b.y < 0) { it.remove(); continue; }
            for (Enemy e : enemies) {
                if (!e.alive) continue;
                if (Math.abs(b.x - e.x) < 18 && Math.abs(b.y - e.y) < 16) {
                    e.alive = false;
                    score += (e.type + 1) * 100 + (wave - 1) * 20;
                    if (e.hasCaptured) { dualShot = true; }  // rescued
                    it.remove(); break;
                }
            }
        }

        // Enemy bullets
        for (Iterator<Bullet> it = eBullets.iterator(); it.hasNext(); ) {
            Bullet b = it.next();
            b.y += b.vy;
            if (b.y > H) { it.remove(); continue; }
            if (!capturedByBeam && Math.abs(b.x - crafX) < 20 && Math.abs(b.y - crafY) < 18) {
                it.remove();
                takeDamage();
                return;
            }
        }

        // Enemy shoot (random, ~every 1.5 s per wave scaling)
        int shootInterval = Math.max(25, 80 - wave * 8);
        if (tick % shootInterval == 0) {
            List<Enemy> alive = new ArrayList<>();
            for (Enemy e : enemies) if (e.alive && e.state != EnemyState.DIVING) alive.add(e);
            if (!alive.isEmpty()) {
                Enemy shooter = alive.get(rng.nextInt(alive.size()));
                eBullets.add(new Bullet(shooter.x, shooter.y + 20, 4 + wave * 0.5));
            }
        }

        // Trigger a dive every few seconds
        if (tick % 120 == 0) triggerDive();

        // Tractor beam trigger (boss enemy, rare)
        if (tick % 300 == 0 && !capturedByBeam) {
            for (Enemy e : enemies) {
                if (e.alive && e.type == 2 && e.state == EnemyState.FORMATION && rng.nextInt(3) == 0) {
                    beams.add(new TractorBeam(e));
                    break;
                }
            }
        }

        // All enemies dead → next wave
        boolean anyAlive = false;
        for (Enemy e : enemies) if (e.alive) { anyAlive = true; break; }
        if (!anyAlive) {
            wave++;
            if (wave > 5) { won = true; timer.stop(); return; }
            spawnWave();
        }
    }

    void updateEnemies() {
        for (Enemy e : enemies) {
            if (!e.alive) continue;
            switch (e.state) {
                case FORMATION -> {
                    // Hover in formation with offset
                    e.x = e.homeX + formOffX;
                    e.y = e.homeY;
                }
                case DIVING -> {
                    // Fly toward player, then loop back
                    e.diveTimer++;
                    double targetX = crafX, targetY = crafY;
                    double dx = targetX - e.x, dy = targetY - e.y;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    double spd = 3.5 + wave * 0.4;
                    if (dist > 5) { e.vx = dx / dist * spd; e.vy = dy / dist * spd; }
                    e.x += e.vx; e.y += e.vy;

                    // Collision with craft during dive
                    if (Math.abs(e.x - crafX) < 22 && Math.abs(e.y - crafY) < 22) {
                        e.state = EnemyState.RETURNING;
                        takeDamage(); return;
                    }
                    // Return to formation after flying past or timeout
                    if (e.y > H + 20 || e.diveTimer > 240) {
                        e.x = e.homeX + formOffX; e.y = -30; e.state = EnemyState.RETURNING;
                    }
                }
                case RETURNING -> {
                    double tx = e.homeX + formOffX, ty = e.homeY;
                    double dx = tx - e.x, dy = ty - e.y;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < 4) { e.x = tx; e.y = ty; e.state = EnemyState.FORMATION; }
                    else { e.x += dx / dist * 3; e.y += dy / dist * 3; }
                }
            }
        }
    }

    void updateBeams() {
        for (Iterator<TractorBeam> it = beams.iterator(); it.hasNext(); ) {
            TractorBeam b = it.next();
            if (!b.owner.alive) { it.remove(); continue; }
            b.x = b.owner.x;
            b.y = b.owner.y + 20;
            b.h = Math.min(b.h + 5, H - b.y);
            b.timer--;
            if (b.timer <= 0) { it.remove(); continue; }

            // Check if beam reaches craft
            if (!capturedByBeam && b.h > crafY - b.y - 18 &&
                Math.abs(b.x - crafX) < 20) {
                capturedByBeam = true;
                captureTimer = 180;
                b.owner.hasCaptured = true;
            }
        }
        // Release player after capture timer
        if (capturedByBeam) {
            captureTimer--;
            crafX = beams.isEmpty() ? crafX : beams.get(0).x;
            crafY = Math.max(H - 70, crafY - 1);
            if (captureTimer <= 0) {
                capturedByBeam = false;
                takeDamage();
            }
        }
    }

    void triggerDive() {
        List<Enemy> candidates = new ArrayList<>();
        for (Enemy e : enemies) if (e.alive && e.state == EnemyState.FORMATION) candidates.add(e);
        if (candidates.isEmpty()) return;
        int count = Math.min(2 + wave / 2, 4);
        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            int idx = rng.nextInt(candidates.size());
            Enemy e = candidates.remove(idx);
            e.state = EnemyState.DIVING;
            e.diveTimer = 0;
            e.vx = 0; e.vy = 0;
        }
    }

    void takeDamage() {
        if (dualShot) { dualShot = false; return; } // lose bonus shot first
        lives--;
        capturedByBeam = false;
        crafX = W / 2.0; crafY = H - 70;
        if (lives <= 0) { gameOver = true; timer.stop(); }
    }

    // ── Rendering ─────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Starfield
        g2.setColor(new Color(80, 100, 200, 55));
        for (int i = 0; i < 70; i++) g2.fillOval((i * 131 + tick / 3) % W, (i * 79) % H, 2, 2);

        // Tractor beams
        for (TractorBeam b : beams) {
            int alpha = 80 + (int)(40 * Math.sin(tick * 0.15));
            g2.setColor(new Color(100, 255, 200, alpha));
            g2.fillRect((int)b.x - 12, (int)b.y, 24, (int)b.h);
            g2.setColor(new Color(0, 255, 180, alpha + 40));
            g2.drawRect((int)b.x - 12, (int)b.y, 24, (int)b.h);
        }

        // Enemies
        for (Enemy e : enemies) if (e.alive) drawEnemy(g2, e);

        // Player bullets
        g2.setColor(new Color(100, 220, 255));
        for (Bullet b : bullets) if (b.active)
            g2.fillRect((int)b.x - 2, (int)b.y - 7, 4, 14);

        // Enemy bullets
        g2.setColor(new Color(255, 120, 40));
        for (Bullet b : eBullets) if (b.active)
            g2.fillOval((int)b.x - 4, (int)b.y - 4, 9, 9);

        // Player craft
        drawCraft(g2, (int)crafX, (int)crafY, dualShot);

        // HUD
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        g2.drawString("FUSION SWARM", 20, 25);
        g2.drawString("Score: " + score, 20, 45);
        g2.drawString("Lives: " + lives, 20, 65);
        g2.drawString("Wave: "  + wave + "/5", 20, 85);
        if (dualShot) {
            g2.setColor(new Color(0, 255, 180));
            g2.drawString("DUAL SHOT!", 20, 105);
        }
        g2.setColor(new Color(140, 140, 180));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g2.drawString("← → Move   SPACE Fire   R Restart", W - 270, H - 12);

        if (capturedByBeam) {
            g2.setColor(new Color(100, 255, 200));
            g2.setFont(new Font("Monospaced", Font.BOLD, 14));
            g2.drawString("TRACTOR BEAM! Destroy the captor!", W / 2 - 180, H - 40);
        }

        if (gameOver) drawOverlay(g2, "CONTAINMENT FAILED!", new Color(255, 70, 70));
        else if (won)  drawOverlay(g2, "ION SWARMS DEFEATED!", new Color(0, 255, 160));
    }

    static final Color[] E_COLS_BASE = {
        new Color(255, 100, 50),
        new Color(120, 80, 255),
        new Color(50, 220, 80)
    };

    void drawEnemy(Graphics2D g2, Enemy e) {
        Color base = E_COLS_BASE[e.type];
        int x = (int)e.x, y = (int)e.y;
        g2.setColor(base);
        g2.fillOval(x - 16, y - 14, 32, 28);
        g2.setColor(base.brighter());
        g2.drawOval(x - 16, y - 14, 32, 28);
        // Eyes
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillOval(x - 9, y - 7, 7, 7);
        g2.fillOval(x + 2, y - 7, 7, 7);
        // Prongs (antennae)
        g2.setColor(base.brighter());
        g2.drawLine(x - 8, y - 14, x - 12, y - 22);
        g2.drawLine(x + 8, y - 14, x + 12, y - 22);
        if (e.type == 2) {
            // Boss ring
            g2.setColor(new Color(255, 255, 100, 160));
            g2.drawOval(x - 22, y - 20, 44, 40);
        }
        if (e.hasCaptured) {
            g2.setColor(new Color(0, 255, 180));
            g2.setFont(new Font("Monospaced", Font.BOLD, 9));
            g2.drawString("CAP", x - 10, y - 17);
        }
    }

    void drawCraft(Graphics2D g2, int x, int y, boolean dual) {
        // Main craft
        g2.setColor(new Color(60, 180, 255));
        int[] xs = {x,      x - 18, x - 8,  x + 8,  x + 18};
        int[] ys = {y - 22, y + 10,  y + 4,  y + 4,  y + 10};
        g2.fillPolygon(xs, ys, 5);
        g2.setColor(new Color(140, 220, 255));
        g2.drawPolygon(xs, ys, 5);
        // Engine glow
        g2.setColor(new Color(255, 160, 30, 180));
        g2.fillOval(x - 5, y + 8, 10, 10);

        // Dual-shot second craft (ghosted alongside)
        if (dual) {
            g2.setColor(new Color(60, 180, 255, 120));
            int[] xs2 = {x + 40,      x + 22, x + 32,  x + 48,  x + 58};
            int[] ys2 = {y - 22, y + 10,  y + 4,  y + 4,  y + 10};
            g2.fillPolygon(xs2, ys2, 5);
        }
    }

    void drawOverlay(Graphics2D g2, String msg, Color col) {
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRoundRect(90, H / 2 - 38, W - 180, 90, 16, 16);
        g2.setColor(col);
        g2.setFont(new Font("Monospaced", Font.BOLD, 22));
        g2.drawString(msg, 110, H / 2 + 5);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g2.drawString("Score: " + score + "   Press R to restart", 110, H / 2 + 32);
    }

    // ── Key handling ──────────────────────────────────────────────
    @Override public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> moveLeft  = true;
            case KeyEvent.VK_RIGHT -> moveRight = true;
            case KeyEvent.VK_SPACE -> {
                if (!gameOver && !won) {
                    if (bullets.size() < 4)
                        bullets.add(new Bullet(crafX, crafY - 24, -10));
                    if (dualShot && bullets.size() < 6)
                        bullets.add(new Bullet(crafX + 38, crafY - 24, -10));
                }
            }
            case KeyEvent.VK_R -> reset();
        }
    }
    @Override public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_LEFT)  moveLeft  = false;
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) moveRight = false;
    }
    @Override public void keyTyped(KeyEvent e) {}

    // ── Entry point ───────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Fusion Swarm");
            FusionSwarm g = new FusionSwarm();
            f.add(g); f.pack();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            g.requestFocusInWindow();
        });
    }
}
