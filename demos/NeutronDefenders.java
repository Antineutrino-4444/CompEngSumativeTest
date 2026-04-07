import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * NEUTRON DEFENDERS – Space Invaders-style fusion core defense demo
 * ──────────────────────────────────────────────────────────────────
 * Defend the fusion core from descending waves of neutron clusters,
 * ion storms, and rogue nanobots. Three weapon modes let you adapt
 * to each enemy type.
 *
 * Controls:  LEFT / RIGHT  – move cannon
 *            SPACE          – fire
 *            1 / 2 / 3      – switch weapon mode
 *            R              – restart
 */
public class NeutronDefenders extends JPanel implements KeyListener {

    static final int W = 620, H = 700;
    static final int ROWS = 4, COLS = 10;

    // ── Weapon modes ─────────────────────────────────────────────
    static final int MODE_PULSE   = 0;  // magnetic pulse  – fast, narrow
    static final int MODE_COOLANT = 1;  // coolant burst   – slow, wide
    static final int MODE_BEAM    = 2;  // containment beam– instant column

    // ── Inner classes ────────────────────────────────────────────
    static class Invader {
        int x, y;
        boolean alive;
        int type; // 0=neutron 1=ion 2=nanobot
        Invader(int x, int y, int type) { this.x = x; this.y = y; this.type = type; alive = true; }
    }

    static class Bullet {
        double x, y, vy, w, h;
        boolean active;
        Bullet(double x, double y, double vy, double w, double h) {
            this.x = x; this.y = y; this.vy = vy; this.w = w; this.h = h; active = true;
        }
    }

    static class Shield {
        int x, y, hp;
        Shield(int x, int y) { this.x = x; this.y = y; hp = 4; }
    }

    // ── Game state ───────────────────────────────────────────────
    Invader[][] invaders = new Invader[ROWS][COLS];
    int formX = 0, formY = 0;   // formation offset
    int formDir = 1;             // 1=right, -1=left
    int formSpeed = 1;

    int cannonX;
    boolean moveLeft, moveRight;

    List<Bullet>  bullets      = new ArrayList<>();
    List<Bullet>  enemyBullets = new ArrayList<>();
    List<Shield>  shields      = new ArrayList<>();

    int weaponMode = MODE_PULSE;
    int score, lives;
    int wave;
    boolean gameOver, won;
    int tick;
    Random rng = new Random();
    Timer  timer;

    // ── Constructor ──────────────────────────────────────────────
    NeutronDefenders() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(4, 4, 18));
        setFocusable(true);
        addKeyListener(this);
        reset();
    }

    void reset() {
        wave = 1;
        score = 0; lives = 3;
        gameOver = won = false;
        cannonX = W / 2;
        spawnWave();
        spawnShields();
        if (timer != null) timer.stop();
        timer = new Timer(16, e -> { update(); repaint(); });
        timer.start();
    }

    void spawnWave() {
        formX = 0; formY = 0; formDir = 1;
        formSpeed = 1 + (wave - 1) / 2;
        bullets.clear(); enemyBullets.clear(); tick = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int type = r == 0 ? 2 : r < 2 ? 1 : 0;
                invaders[r][c] = new Invader(80 + c * 45, 80 + r * 48, type);
            }
        }
    }

    void spawnShields() {
        shields.clear();
        for (int i = 0; i < 4; i++) {
            shields.add(new Shield(80 + i * 140, H - 150));
        }
    }

    // ── Update loop ──────────────────────────────────────────────
    void update() {
        if (gameOver || won) return;
        tick++;

        // Cannon movement
        if (moveLeft  && cannonX > 30)    cannonX -= 4;
        if (moveRight && cannonX < W - 30) cannonX += 4;

        // Formation movement
        if (tick % 2 == 0) {
            formX += formDir * formSpeed;
            // Check formation edges
            int leftEdge  = Integer.MAX_VALUE;
            int rightEdge = Integer.MIN_VALUE;
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    if (invaders[r][c].alive) {
                        int ix = invaders[r][c].x + formX;
                        leftEdge  = Math.min(leftEdge, ix);
                        rightEdge = Math.max(rightEdge, ix + 34);
                    }
            if (rightEdge >= W - 10) { formDir = -1; formY += 20; }
            if (leftEdge  <= 10)     { formDir =  1; formY += 20; }
        }

        // Check invaders reached cannon line
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (invaders[r][c].alive && invaders[r][c].y + formY > H - 120) {
                    gameOver = true; timer.stop(); return;
                }

        // Player bullets
        Iterator<Bullet> bit = bullets.iterator();
        while (bit.hasNext()) {
            Bullet b = bit.next();
            b.y += b.vy;
            if (b.y < 0) { bit.remove(); continue; }
            // Check invader hit
            boolean hit = false;
            for (int r = 0; r < ROWS && !hit; r++) {
                for (int c = 0; c < COLS && !hit; c++) {
                    Invader inv = invaders[r][c];
                    if (!inv.alive) continue;
                    int ix = inv.x + formX, iy = inv.y + formY;
                    if (b.x < ix + 34 && b.x + b.w > ix && b.y < iy + 30 && b.y + b.h > iy) {
                        inv.alive = false;
                        score += (inv.type + 1) * 100;
                        if (weaponMode != MODE_BEAM) { bit.remove(); hit = true; }
                    }
                }
            }
            // Check shield hit
            if (!hit) {
                for (Shield s : shields) {
                    if (b.x < s.x + 70 && b.x + b.w > s.x && b.y < s.y + 40 && b.y + b.h > s.y) {
                        // player bullet passes shields; shields only block enemy
                    }
                }
            }
        }

        // Enemy bullets
        Iterator<Bullet> ebit = enemyBullets.iterator();
        while (ebit.hasNext()) {
            Bullet b = ebit.next();
            b.y += b.vy;
            if (b.y > H) { ebit.remove(); continue; }
            // Shield hits
            boolean shieldHit = false;
            for (Shield s : shields) {
                if (s.hp > 0 && b.x > s.x && b.x < s.x + 70 && b.y > s.y && b.y < s.y + 40) {
                    s.hp--; ebit.remove(); shieldHit = true; break;
                }
            }
            if (shieldHit) continue;
            // Cannon hit
            if (Math.abs(b.x - cannonX) < 22 && b.y > H - 80) {
                ebit.remove();
                lives--;
                if (lives <= 0) { gameOver = true; timer.stop(); return; }
            }
        }

        // Enemy shoot (one random invader every ~1.5 s)
        if (tick % 90 == 0) {
            List<Invader> alive = new ArrayList<>();
            for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++)
                if (invaders[r][c].alive) alive.add(invaders[r][c]);
            if (!alive.isEmpty()) {
                Invader shooter = alive.get(rng.nextInt(alive.size()));
                enemyBullets.add(new Bullet(shooter.x + formX + 15, shooter.y + formY + 30, 3 + wave * 0.4, 4, 10));
            }
        }

        // Wave clear?
        boolean anyAlive = false;
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++)
            if (invaders[r][c].alive) { anyAlive = true; break; }
        if (!anyAlive) {
            wave++;
            if (wave > 4) { won = true; timer.stop(); return; }
            spawnWave();
        }
    }

    // ── Fire ─────────────────────────────────────────────────────
    void fire() {
        if (gameOver || won) return;
        if (weaponMode == MODE_PULSE && bullets.size() < 2) {
            bullets.add(new Bullet(cannonX - 2, H - 95, -10, 4, 14));
        } else if (weaponMode == MODE_COOLANT && bullets.size() < 1) {
            bullets.add(new Bullet(cannonX - 8, H - 95, -5, 16, 10));
        } else if (weaponMode == MODE_BEAM) {
            // Instant: add tall column bullet
            bullets.add(new Bullet(cannonX - 2, 0, 0, 4, H));
        }
    }

    // ── Rendering ────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Starfield
        g2.setColor(new Color(100, 120, 200, 60));
        for (int i = 0; i < 60; i++) g2.fillOval((i * 113) % W, (i * 79) % H, 2, 2);

        // Invaders
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                Invader inv = invaders[r][c];
                if (inv.alive) drawInvader(g2, inv.x + formX, inv.y + formY, inv.type);
            }

        // Shields
        for (Shield s : shields) if (s.hp > 0) drawShield(g2, s);

        // Player bullets
        for (Bullet b : bullets) {
            if (!b.active) continue;
            Color bc = weaponMode == MODE_PULSE   ? new Color(100, 220, 255) :
                       weaponMode == MODE_COOLANT  ? new Color(150, 255, 255) :
                                                     new Color(255, 255, 100, 120);
            g2.setColor(bc);
            g2.fillRect((int)b.x, (int)b.y, (int)b.w, (int)b.h);
        }

        // Enemy bullets
        g2.setColor(new Color(255, 100, 50));
        for (Bullet b : enemyBullets) g2.fillOval((int)b.x - 4, (int)b.y - 4, (int)b.w + 4, (int)b.h);

        // Cannon
        drawCannon(g2);

        // HUD
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        g2.drawString("NEUTRON DEFENDERS", 20, 25);
        g2.drawString("Score: " + score, 20, 45);
        g2.drawString("Lives: " + lives, 20, 65);
        g2.drawString("Wave: "  + wave + "/4", 20, 85);

        // Weapon selector
        String[] modes = {"1:Pulse", "2:Coolant", "3:Beam"};
        Color[] mCols = {new Color(100,220,255), new Color(150,255,255), new Color(255,255,100)};
        for (int i = 0; i < 3; i++) {
            g2.setColor(weaponMode == i ? mCols[i] : new Color(80,80,80));
            g2.fillRoundRect(W - 130, 20 + i * 22, 110, 18, 6, 6);
            g2.setColor(weaponMode == i ? Color.BLACK : Color.GRAY);
            g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
            g2.drawString(modes[i], W - 126, 33 + i * 22);
        }

        g2.setColor(new Color(140, 140, 180));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g2.drawString("← → Move   SPACE Fire   1/2/3 Weapon   R Restart", 20, H - 12);

        if (gameOver) drawOverlay(g2, "CORE BREACHED!", new Color(255, 70, 70));
        else if (won)  drawOverlay(g2, "FUSION CORE DEFENDED!", new Color(0, 255, 160));
    }

    static final Color[] INV_COLORS = {
        new Color(255, 100, 50),  // neutron
        new Color(120, 80, 255),  // ion
        new Color(80, 220, 80)    // nanobot
    };

    void drawInvader(Graphics2D g2, int x, int y, int type) {
        Color c = INV_COLORS[type];
        g2.setColor(c);
        g2.fillRoundRect(x, y, 34, 28, 8, 8);
        g2.setColor(c.darker());
        g2.drawRoundRect(x, y, 34, 28, 8, 8);
        // Eyes / sensors
        g2.setColor(Color.BLACK);
        g2.fillOval(x + 6, y + 7, 7, 7);
        g2.fillOval(x + 21, y + 7, 7, 7);
        // Type indicator
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 8));
        String[] sym = {"n", "i", "b"};
        g2.drawString(sym[type], x + 14, y + 26);
        // Antennae
        g2.setColor(c.brighter());
        g2.drawLine(x + 8, y, x + 4, y - 6);
        g2.drawLine(x + 26, y, x + 30, y - 6);
    }

    void drawShield(Graphics2D g2, Shield s) {
        int alpha    = Math.min(255, 60 + s.hp * 40);
        int alphaBdr = Math.min(255, alpha + 40);
        g2.setColor(new Color(0, 180, 100, alpha));
        g2.fillRoundRect(s.x, s.y, 70, 40, 10, 10);
        g2.setColor(new Color(0, 255, 140, alphaBdr));
        g2.drawRoundRect(s.x, s.y, 70, 40, 10, 10);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.drawString("HP:" + s.hp, s.x + 20, s.y + 25);
    }

    void drawCannon(Graphics2D g2) {
        int y = H - 85;
        g2.setColor(new Color(60, 180, 255));
        // Base
        g2.fillRoundRect(cannonX - 22, y + 14, 44, 20, 8, 8);
        // Barrel
        g2.fillRect(cannonX - 5, y, 10, 20);
        g2.setColor(new Color(140, 220, 255));
        g2.drawRoundRect(cannonX - 22, y + 14, 44, 20, 8, 8);
    }

    void drawOverlay(Graphics2D g2, String msg, Color col) {
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRoundRect(100, 290, W - 200, 95, 16, 16);
        g2.setColor(col);
        g2.setFont(new Font("Monospaced", Font.BOLD, 22));
        g2.drawString(msg, 120, 335);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g2.drawString("Score: " + score + "   Press R to restart", 120, 365);
    }

    // ── Key handling ─────────────────────────────────────────────
    @Override public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> moveLeft  = true;
            case KeyEvent.VK_RIGHT -> moveRight = true;
            case KeyEvent.VK_SPACE -> fire();
            case KeyEvent.VK_1     -> weaponMode = MODE_PULSE;
            case KeyEvent.VK_2     -> weaponMode = MODE_COOLANT;
            case KeyEvent.VK_3     -> weaponMode = MODE_BEAM;
            case KeyEvent.VK_R     -> reset();
        }
    }
    @Override public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_LEFT)  moveLeft  = false;
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) moveRight = false;
    }
    @Override public void keyTyped(KeyEvent e) {}

    // ── Entry point ──────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Neutron Defenders");
            NeutronDefenders g = new NeutronDefenders();
            f.add(g); f.pack();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            g.requestFocusInWindow();
        });
    }
}
