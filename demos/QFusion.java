import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Q-FUSION: Stabilize the Core
 * ─────────────────────────────────────────────────────────────────
 * A Q-Bert-inspired reactor-stabilization game.
 *
 * Story: A fusion-reactor lattice is losing stability. Hop across
 *        the isometric node pyramid to bring every node to its
 *        STABILIZED (green) state before neutron-burst enemies
 *        knock you off or trigger a meltdown.
 *
 * Controls:  Q = jump upper-left   E = jump upper-right
 *            Z = jump lower-left   C = jump lower-right
 *            R = restart
 *
 * Node states: OFFLINE (dark) → CHARGING (yellow) → STABILIZED (green)
 *              Stepping on a node advances its state by one.
 *              Already STABILIZED nodes stay stabilized.
 */
public class QFusion extends JPanel implements KeyListener {

    // ── Node-state constants ─────────────────────────────────────
    static final int OFFLINE    = 0;
    static final int CHARGING   = 1;
    static final int STABILIZED = 2;

    // Top-face colours for each state
    static final Color[] TOP_COLOR = {
        new Color(55, 55, 85),     // OFFLINE   – dark blue-grey
        new Color(210, 165, 0),    // CHARGING  – amber
        new Color(0, 175, 85)      // STABILIZED– green
    };

    // ── Pyramid geometry ────────────────────────────────────────
    static final int ROWS   = 7;           // rows 0-6, row r has (r+1) nodes
    static final int TW     = 60;          // tile width
    static final int TH     = 30;          // tile top-face half-height
    static final int TD     = 16;          // tile depth (side face height)
    static final int OX     = 400;         // screen-centre X for node (0,0)
    static final int OY     = 70;          // screen Y for node (0,0)

    // ── Game state ───────────────────────────────────────────────
    int[][] nodeState = new int[ROWS][];
    int playerRow, playerCol;
    int score, lives;
    boolean gameOver, won;

    /** Simple enemy: a neutron burst hopping down the pyramid */
    static class Enemy {
        int row, col, dir; // dir: 0=keep col (left), 1=col+1 (right)
        Enemy(int row, int col, int dir) {
            this.row = row; this.col = col; this.dir = dir;
        }
    }
    List<Enemy> enemies = new ArrayList<>();
    int tick;
    Random rng = new Random();
    Timer timer;

    // ── Constructor ──────────────────────────────────────────────
    QFusion() {
        setPreferredSize(new Dimension(800, 650));
        setBackground(new Color(4, 4, 18));
        setFocusable(true);
        addKeyListener(this);
        reset();
    }

    void reset() {
        for (int r = 0; r < ROWS; r++) {
            nodeState[r] = new int[r + 1];
            Arrays.fill(nodeState[r], OFFLINE);
        }
        playerRow = 0;
        playerCol = 0;
        nodeState[0][0] = CHARGING;   // first step always charges top node
        score = 0;
        lives = 3;
        gameOver = won = false;
        enemies.clear();
        tick = 0;
        if (timer != null) timer.stop();
        timer = new Timer(110, e -> { tick(); repaint(); });
        timer.start();
    }

    // ── Advance node state when player lands ────────────────────
    void landOn(int r, int c) {
        int s = nodeState[r][c];
        if (s == OFFLINE)    { nodeState[r][c] = CHARGING;   }
        else if (s == CHARGING)  { nodeState[r][c] = STABILIZED; score += 50; }
        // STABILIZED stays stabilized
    }

    // ── Per-tick game logic ──────────────────────────────────────
    void tick() {
        if (gameOver || won) return;
        tick++;

        // Win check
        boolean allDone = true;
        outer:
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c <= r; c++)
                if (nodeState[r][c] != STABILIZED) { allDone = false; break outer; }
        if (allDone) { won = true; timer.stop(); return; }

        // Spawn an enemy occasionally (max 5)
        if (tick % 28 == 0 && enemies.size() < 5) {
            enemies.add(new Enemy(0, 0, rng.nextInt(2)));
        }

        // Move enemies every 7 ticks
        if (tick % 7 == 0) {
            List<Enemy> remove = new ArrayList<>();
            for (Enemy e : enemies) {
                int nr = e.row + 1;
                int nc = e.dir == 0 ? e.col : e.col + 1;
                if (nr >= ROWS || nc < 0 || nc > nr) { remove.add(e); continue; }
                e.row = nr; e.col = nc;
                if (e.row == playerRow && e.col == playerCol) hitEnemy(e, remove);
            }
            enemies.removeAll(remove);
        }
    }

    void hitEnemy(Enemy e, List<Enemy> remove) {
        remove.add(e);
        lives--;
        if (lives <= 0) { gameOver = true; timer.stop(); }
    }

    // ── Player movement ──────────────────────────────────────────
    void tryMove(int dr, int dc) {
        if (gameOver || won) return;
        int nr = playerRow + dr;
        int nc = playerCol + dc;
        if (nr < 0 || nr >= ROWS || nc < 0 || nc > nr) {
            // Fell off pyramid
            lives--;
            if (lives <= 0) { gameOver = true; timer.stop(); }
            playerRow = 0; playerCol = 0;   // respawn at top
            return;
        }
        playerRow = nr; playerCol = nc;
        landOn(playerRow, playerCol);

        // Check immediate enemy collision
        List<Enemy> remove = new ArrayList<>();
        for (Enemy e : new ArrayList<>(enemies)) {
            if (e.row == playerRow && e.col == playerCol) hitEnemy(e, remove);
        }
        enemies.removeAll(remove);
        repaint();
    }

    // ── Screen position of node top-centre ──────────────────────
    // x = OX + col*TW – row*TW/2    (shifts left by half-tile per row)
    // y = OY + row*(TH + TD)
    Point nodePos(int r, int c) {
        int x = OX + c * TW - r * TW / 2;
        int y = OY + r * (TH + TD);
        return new Point(x, y);
    }

    // ── Rendering ────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw starfield background
        g2.setColor(new Color(30, 60, 120, 60));
        for (int i = 0; i < 60; i++) {
            g2.fillOval((i * 131 + 7) % 800, (i * 79) % 650, 2, 2);
        }

        // Title
        g2.setColor(new Color(0, 230, 180));
        g2.setFont(new Font("Monospaced", Font.BOLD, 20));
        g2.drawString("Q-FUSION: STABILIZE THE CORE", 140, 35);

        // HUD
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g2.drawString("Score: " + score, 20, 25);
        g2.drawString("Lives: " + lives, 20, 45);

        // Legend
        String[] labels = {"Offline", "Charging", "Stabilized"};
        for (int s = 0; s < 3; s++) {
            g2.setColor(TOP_COLOR[s]);
            g2.fillRect(620, 15 + s * 22, 20, 14);
            g2.setColor(Color.LIGHT_GRAY);
            g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
            g2.drawString(labels[s], 644, 27 + s * 22);
        }

        // Controls hint
        g2.setColor(new Color(160, 160, 200));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g2.drawString("Q=↖  E=↗  Z=↙  C=↘   R=restart", 520, 590);

        // Draw pyramid nodes bottom-to-top, right-to-left for correct overlap
        for (int r = ROWS - 1; r >= 0; r--)
            for (int c = r; c >= 0; c--)
                drawCube(g2, r, c);

        // Draw enemies (neutron bursts)
        for (Enemy e : enemies) {
            Point p = nodePos(e.row, e.col);
            drawNeutronBurst(g2, p.x, p.y - TH / 2 - 14);
        }

        // Draw player
        Point pp = nodePos(playerRow, playerCol);
        drawPlayer(g2, pp.x, pp.y - TH / 2 - 14);

        // Overlay messages
        if (gameOver) drawOverlay(g2, "REACTOR MELTDOWN!", new Color(255, 70, 70));
        else if (won)  drawOverlay(g2, "REACTOR STABILIZED!", new Color(0, 255, 160));
    }

    void drawCube(Graphics2D g2, int r, int c) {
        Point p = nodePos(r, c);
        int x = p.x, y = p.y;
        Color base = TOP_COLOR[nodeState[r][c]];

        // Top diamond
        Polygon top = diamond(x, y, TW, TH);
        g2.setColor(base);
        g2.fillPolygon(top);
        g2.setColor(base.brighter());
        g2.drawPolygon(top);

        // Right face
        Polygon right = new Polygon(
            new int[]{x + TW / 2, x,         x,             x + TW / 2},
            new int[]{y,          y + TH / 2, y + TH / 2 + TD, y + TD    }, 4);
        g2.setColor(darken(base, 0.55f));
        g2.fillPolygon(right);
        g2.setColor(Color.BLACK);
        g2.drawPolygon(right);

        // Left face
        Polygon left = new Polygon(
            new int[]{x - TW / 2, x,             x,         x - TW / 2},
            new int[]{y,          y + TH / 2,     y + TH / 2 + TD, y + TD}, 4);
        g2.setColor(darken(base, 0.35f));
        g2.fillPolygon(left);
        g2.setColor(Color.BLACK);
        g2.drawPolygon(left);
    }

    /** Diamond / rhombus polygon centred at (cx, cy). */
    Polygon diamond(int cx, int cy, int w, int h) {
        return new Polygon(
            new int[]{cx,         cx + w / 2, cx,         cx - w / 2},
            new int[]{cy - h / 2, cy,         cy + h / 2, cy        }, 4);
    }

    Color darken(Color c, float factor) {
        return new Color(
            (int)(c.getRed()   * factor),
            (int)(c.getGreen() * factor),
            (int)(c.getBlue()  * factor));
    }

    void drawPlayer(Graphics2D g2, int x, int y) {
        // Blue diamond with white core
        g2.setColor(new Color(30, 170, 255));
        g2.fillOval(x - 12, y - 12, 24, 24);
        g2.setColor(new Color(180, 230, 255));
        g2.fillOval(x - 6, y - 6, 12, 12);
        g2.setColor(Color.WHITE);
        g2.drawOval(x - 12, y - 12, 24, 24);
    }

    void drawNeutronBurst(Graphics2D g2, int x, int y) {
        // Orange/red starburst
        g2.setColor(new Color(255, 100, 20));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int x2 = x + (int)(Math.cos(a) * 12);
            int y2 = y + (int)(Math.sin(a) * 12);
            g2.drawLine(x, y, x2, y2);
        }
        g2.setColor(new Color(255, 200, 50));
        g2.fillOval(x - 6, y - 6, 12, 12);
        g2.setColor(Color.WHITE);
        g2.fillOval(x - 3, y - 3, 6, 6);
    }

    void drawOverlay(Graphics2D g2, String msg, Color col) {
        g2.setColor(new Color(0, 0, 0, 175));
        g2.fillRoundRect(160, 240, 480, 100, 18, 18);
        g2.setColor(col);
        g2.setFont(new Font("Monospaced", Font.BOLD, 24));
        g2.drawString(msg, 195, 285);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g2.drawString("Score: " + score + "   Press R to restart", 215, 320);
    }

    // ── Key handling ─────────────────────────────────────────────
    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_Q -> tryMove(-1, -1); // upper-left
            case KeyEvent.VK_E -> tryMove(-1,  0); // upper-right
            case KeyEvent.VK_Z -> tryMove( 1,  0); // lower-left
            case KeyEvent.VK_C -> tryMove( 1,  1); // lower-right
            case KeyEvent.VK_R -> reset();
        }
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    // ── Entry point ──────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Q-Fusion: Stabilize the Core");
            QFusion g = new QFusion();
            f.add(g);
            f.pack();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            g.requestFocusInWindow();
        });
    }
}
