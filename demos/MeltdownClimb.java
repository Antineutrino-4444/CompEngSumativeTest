import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * MELTDOWN CLIMB – Donkey Kong-style reactor tower demo
 * ──────────────────────────────────────────────────────
 * Climb a reactor tower to reach the emergency shutdown panel
 * at the top. A sabotage robot drops radioactive waste drums
 * and plasma bolts from above. Jump over hazards, use coolant
 * sprays, and reach the goal before the meltdown timer runs out.
 *
 * Controls:  LEFT / RIGHT – run
 *            UP / SPACE   – jump (while on ground or ladder)
 *            UP           – climb ladder (when near ladder)
 *            R            – restart
 */
public class MeltdownClimb extends JPanel implements KeyListener {

    static final int W = 600, H = 680;
    static final int GRAVITY = 1;
    static final int JUMP_VY = -16;

    // ── Platform layout: {x, y, width} ───────────────────────────
    static final int[][] PLATFORMS = {
        {  0, 590, 600 },   // floor
        { 40, 480, 510 },   // level 1
        { 50, 370, 500 },   // level 2
        { 40, 260, 510 },   // level 3
        { 50, 150, 500 },   // level 4  (top)
    };

    // Ladder: {x, bottomY, topY}  (x = centre pixel)
    static final int[][] LADDERS = {
        { 520, 480, 590 },
        {  80, 370, 480 },
        { 520, 260, 370 },
        {  80, 150, 260 },
    };

    // Goal position (top platform)
    static final int GOAL_X = 240, GOAL_Y = 130, GOAL_W = 80, GOAL_H = 20;

    // ── Inner classes ─────────────────────────────────────────────
    static class Player {
        double x, y, vx, vy;
        boolean onGround, onLadder, alive;
        int invincibleTicks;
        Player() { reset(); }
        void reset() {
            x = 80; y = 550; vx = vy = 0;
            onGround = true; onLadder = false; alive = true; invincibleTicks = 0;
        }
    }

    static class Barrel {
        double x, y, vx, vy;
        boolean onGround;
        int type; // 0=waste drum, 1=plasma bolt
        boolean active = true;
        Barrel(double x, double y, int type) {
            this.x = x; this.y = y; this.type = type;
            vx = (type == 1 ? -5 : -2.5) + (Math.random() - 0.5) * 1.5;
            vy = 0;
        }
    }

    // ── Game state ───────────────────────────────────────────────
    Player player = new Player();
    List<Barrel> barrels = new ArrayList<>();
    int  score, lives;
    int  meltdownTimer;   // counts down; 0 = meltdown
    boolean gameOver, won;
    boolean moveLeft, moveRight, jumpPressed;
    int  tick;
    int  barrelSpawnInterval;
    Random rng = new Random();
    Timer  gameTimer;

    MeltdownClimb() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(10, 5, 5));
        setFocusable(true);
        addKeyListener(this);
        reset();
    }

    void reset() {
        player.reset();
        barrels.clear();
        score = 0; lives = 3;
        meltdownTimer = 1800;    // ~30 seconds at 60 fps
        gameOver = won = false;
        tick = 0;
        barrelSpawnInterval = 90;
        if (gameTimer != null) gameTimer.stop();
        gameTimer = new Timer(16, e -> { update(); repaint(); });
        gameTimer.start();
    }

    // ── Physics helpers ──────────────────────────────────────────
    boolean isOnPlatform(double x, double y) {
        for (int[] p : PLATFORMS) {
            if (x >= p[0] && x <= p[0] + p[2] && y >= p[1] - 2 && y <= p[1] + 4)
                return true;
        }
        return false;
    }

    int platformY(double x, double fromY) {
        // Returns the Y of the nearest platform below (fromY), or -1
        int best = Integer.MAX_VALUE;
        for (int[] p : PLATFORMS) {
            if (x >= p[0] && x <= p[0] + p[2] && p[1] >= fromY - 2) {
                best = Math.min(best, p[1]);
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    boolean nearLadder(double x, double y) {
        for (int[] l : LADDERS) {
            if (Math.abs(x - l[0]) < 20 && y > l[2] - 10 && y < l[1] + 10) return true;
        }
        return false;
    }

    int[] ladderAt(double x, double y) {
        for (int[] l : LADDERS) {
            if (Math.abs(x - l[0]) < 18 && y > l[2] && y < l[1]) return l;
        }
        return null;
    }

    // ── Game loop ────────────────────────────────────────────────
    void update() {
        if (gameOver || won) return;
        tick++;

        // Meltdown countdown
        meltdownTimer--;
        if (meltdownTimer <= 0) { gameOver = true; gameTimer.stop(); return; }

        updatePlayer();
        updateBarrels();
        spawnBarrels();
        checkCollisions();
    }

    void updatePlayer() {
        Player p = player;
        if (!p.alive) return;
        if (p.invincibleTicks > 0) p.invincibleTicks--;

        // Horizontal
        if (moveLeft)  p.vx = -4;
        else if (moveRight) p.vx = 4;
        else p.vx = 0;

        // Ladder logic
        int[] ladder = ladderAt(p.x, p.y);
        if (ladder != null && jumpPressed) {
            // Climbing up on ladder
            p.onLadder = true;
            p.vy = -3;
            p.vx = 0;
        }
        if (p.onLadder) {
            p.x = ladder != null ? ladder[0] : p.x;
            p.vy = jumpPressed ? -3 : 1;   // auto-slide down if not pressing up
            p.vx = 0;
            if (jumpPressed) score += 1;    // reward climbing
            if (ladder == null || p.y <= ladder[2]) {
                p.onLadder = false;  // reached top of ladder
            }
        }

        // Jump (not on ladder)
        if (!p.onLadder && jumpPressed && p.onGround) {
            p.vy = JUMP_VY;
            p.onGround = false;
        }

        // Gravity
        if (!p.onLadder) p.vy += GRAVITY;

        p.x += p.vx;
        p.y += p.vy;

        // Clamp horizontal
        p.x = Math.max(10, Math.min(W - 10, p.x));

        // Platform collision (only falling down)
        p.onGround = false;
        if (p.vy >= 0) {
            for (int[] plt : PLATFORMS) {
                if (p.x >= plt[0] && p.x <= plt[0] + plt[2] &&
                    p.y + 4 >= plt[1] && p.y - 6 < plt[1]) {
                    p.y = plt[1] - 4;
                    p.vy = 0;
                    p.onGround = true;
                }
            }
        }

        // Fell off bottom
        if (p.y > H + 20) {
            hitPlayer(); return;
        }

        // Reached goal
        if (p.x >= GOAL_X && p.x <= GOAL_X + GOAL_W && p.y <= GOAL_Y + GOAL_H && p.y >= GOAL_Y - 10) {
            won = true; gameTimer.stop();
        }
    }

    void updateBarrels() {
        for (Barrel b : barrels) {
            if (!b.active) continue;
            b.vy += GRAVITY * 0.6;
            b.x  += b.vx;
            b.y  += b.vy;

            // Platform landing
            if (b.vy >= 0) {
                for (int[] plt : PLATFORMS) {
                    if (b.x >= plt[0] && b.x <= plt[0] + plt[2] &&
                        b.y + 8 >= plt[1] && b.y - 8 < plt[1]) {
                        b.y = plt[1] - 8;
                        b.vy = 0;
                        b.onGround = true;
                        // Roll left
                        if (b.vx == 0) b.vx = -2.5;
                    }
                }
            }

            // Roll off edge (reverse)
            boolean onAny = false;
            for (int[] plt : PLATFORMS) {
                if (b.x >= plt[0] && b.x <= plt[0] + plt[2] && Math.abs(b.y - (plt[1] - 8)) < 4) {
                    onAny = true; break;
                }
            }
            if (!onAny && b.onGround) {
                b.vy = 2;  // fall
                b.onGround = false;
            }

            // Fell off bottom
            if (b.y > H + 30) b.active = false;
            // Scrolled off sides
            if (b.x < -20 || b.x > W + 20) b.active = false;
        }
        barrels.removeIf(b -> !b.active);
    }

    void spawnBarrels() {
        if (tick % barrelSpawnInterval == 0) {
            int type = rng.nextInt(3) == 0 ? 1 : 0;
            barrels.add(new Barrel(530, 130, type));
            barrelSpawnInterval = Math.max(40, 90 - (score / 200) * 5);
        }
    }

    void checkCollisions() {
        if (player.invincibleTicks > 0 || !player.alive) return;
        for (Barrel b : barrels) {
            if (!b.active) continue;
            double dx = b.x - player.x, dy = b.y - player.y;
            if (Math.sqrt(dx * dx + dy * dy) < 22) { hitPlayer(); return; }
        }
    }

    void hitPlayer() {
        lives--;
        player.alive = false;
        if (lives <= 0) { gameOver = true; gameTimer.stop(); return; }
        // Respawn with brief delay
        player.reset();
        player.invincibleTicks = 120;
    }

    // ── Rendering ────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw platforms
        for (int[] p : PLATFORMS) {
            g2.setColor(new Color(60, 60, 100));
            g2.fillRect(p[0], p[1], p[2], 12);
            g2.setColor(new Color(100, 100, 180));
            g2.drawRect(p[0], p[1], p[2], 12);
            // Warning stripes
            g2.setColor(new Color(255, 180, 0, 80));
            for (int sx = p[0]; sx < p[0] + p[2]; sx += 20) {
                g2.drawLine(sx, p[1], sx + 10, p[1] + 12);
            }
        }

        // Draw ladders
        for (int[] l : LADDERS) {
            g2.setColor(new Color(100, 180, 80));
            g2.fillRect(l[0] - 6, l[2], 12, l[1] - l[2]);
            g2.setColor(new Color(160, 255, 120));
            for (int ry = l[2]; ry < l[1]; ry += 16) {
                g2.fillRect(l[0] - 12, ry, 24, 5);
            }
        }

        // Draw goal
        g2.setColor(new Color(0, 220, 140));
        g2.fillRoundRect(GOAL_X, GOAL_Y, GOAL_W, GOAL_H, 8, 8);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 10));
        g2.drawString("SHUTDOWN", GOAL_X + 4, GOAL_Y + 14);

        // Sabotage robot at top
        drawSabotBot(g2, 490, 115);

        // Draw barrels
        for (Barrel b : barrels) {
            if (!b.active) continue;
            drawBarrel(g2, (int)b.x, (int)b.y, b.type);
        }

        // Draw player
        if (player.alive && (player.invincibleTicks == 0 || (player.invincibleTicks / 5) % 2 == 0))
            drawPlayer(g2, (int)player.x, (int)player.y);

        // HUD
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        g2.drawString("MELTDOWN CLIMB", 10, 22);
        g2.drawString("Score: " + score, 10, 42);
        g2.drawString("Lives: " + lives, 10, 62);

        // Meltdown timer bar
        int barW = 200;
        double frac = meltdownTimer / 1800.0;
        g2.setColor(new Color(40, 40, 40));
        g2.fillRect(W - barW - 10, 10, barW, 16);
        Color tCol = frac > 0.5 ? new Color(0, 200, 80) : frac > 0.25 ? new Color(255, 180, 0) : new Color(255, 50, 50);
        g2.setColor(tCol);
        g2.fillRect(W - barW - 10, 10, (int)(barW * frac), 16);
        g2.setColor(Color.GRAY);
        g2.drawRect(W - barW - 10, 10, barW, 16);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g2.drawString("MELTDOWN TIMER", W - barW - 10, 40);

        g2.setColor(new Color(140, 140, 180));
        g2.drawString("← → Run  ↑/SPACE Jump/Climb  R Restart", 10, H - 10);

        if (gameOver) drawOverlay(g2, "MELTDOWN! TOWER LOST!", new Color(255, 70, 70));
        else if (won)  drawOverlay(g2, "REACTOR SHUTDOWN! SAVED!", new Color(0, 255, 160));
    }

    void drawPlayer(Graphics2D g2, int x, int y) {
        // Body
        g2.setColor(new Color(80, 180, 255));
        g2.fillRoundRect(x - 12, y - 22, 24, 28, 6, 6);
        // Head / helmet
        g2.setColor(new Color(60, 150, 220));
        g2.fillOval(x - 10, y - 36, 20, 20);
        g2.setColor(new Color(180, 220, 255));
        g2.drawOval(x - 10, y - 36, 20, 20);
        // Visor
        g2.setColor(new Color(0, 200, 255, 180));
        g2.fillArc(x - 7, y - 33, 14, 10, 0, 180);
    }

    void drawBarrel(Graphics2D g2, int x, int y, int type) {
        if (type == 0) {
            // Waste drum
            g2.setColor(new Color(100, 180, 60));
            g2.fillOval(x - 14, y - 14, 28, 28);
            g2.setColor(new Color(60, 120, 40));
            g2.drawOval(x - 14, y - 14, 28, 28);
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("Monospaced", Font.BOLD, 10));
            g2.drawString("RAD", x - 12, y + 4);
        } else {
            // Plasma bolt
            g2.setColor(new Color(255, 180, 20));
            g2.fillOval(x - 10, y - 10, 20, 20);
            g2.setColor(new Color(255, 100, 0));
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                g2.drawLine(x, y, x + (int)(Math.cos(a) * 14), y + (int)(Math.sin(a) * 14));
            }
        }
    }

    void drawSabotBot(Graphics2D g2, int x, int y) {
        g2.setColor(new Color(160, 60, 60));
        g2.fillRoundRect(x - 25, y, 50, 40, 8, 8);
        g2.setColor(new Color(220, 100, 80));
        g2.drawRoundRect(x - 25, y, 50, 40, 8, 8);
        // Eyes
        g2.setColor(new Color(255, 200, 0));
        g2.fillOval(x - 15, y + 8, 10, 10);
        g2.fillOval(x + 5, y + 8, 10, 10);
        // Label
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 9));
        g2.drawString("SABOT", x - 20, y + 35);
    }

    void drawOverlay(Graphics2D g2, String msg, Color col) {
        g2.setColor(new Color(0, 0, 0, 185));
        g2.fillRoundRect(60, H / 2 - 40, W - 120, 90, 16, 16);
        g2.setColor(col);
        g2.setFont(new Font("Monospaced", Font.BOLD, 20));
        g2.drawString(msg, 80, H / 2 + 5);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g2.drawString("Score: " + score + "   Press R to restart", 80, H / 2 + 32);
    }

    // ── Key handling ─────────────────────────────────────────────
    @Override public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> moveLeft   = true;
            case KeyEvent.VK_RIGHT -> moveRight  = true;
            case KeyEvent.VK_UP, KeyEvent.VK_SPACE -> jumpPressed = true;
            case KeyEvent.VK_R     -> reset();
        }
    }
    @Override public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> moveLeft   = false;
            case KeyEvent.VK_RIGHT -> moveRight  = false;
            case KeyEvent.VK_UP, KeyEvent.VK_SPACE -> jumpPressed = false;
        }
    }
    @Override public void keyTyped(KeyEvent e) {}

    // ── Entry point ──────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Meltdown Climb");
            MeltdownClimb g = new MeltdownClimb();
            f.add(g); f.pack();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            g.requestFocusInWindow();
        });
    }
}
