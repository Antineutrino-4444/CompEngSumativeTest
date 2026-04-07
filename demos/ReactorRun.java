import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * REACTOR RUN – Pac-Man-style nuclear facility demo
 * ──────────────────────────────────────────────────
 * You are a maintenance drone collecting unstable fuel cells
 * in a damaged fusion facility. Radiation clouds patrol the
 * corridors. Grab power cells for temporary lead shielding.
 *
 * Controls:  Arrow keys to move   R = restart
 */
public class ReactorRun extends JPanel implements KeyListener {

    // ── Maze constants ───────────────────────────────────────────
    static final int CELL   = 36;   // pixels per maze cell
    static final int COLS   = 19;
    static final int ROWS_M = 19;
    static final int WIN_W  = COLS * CELL + 1;
    static final int WIN_H  = ROWS_M * CELL + 70;

    // Cell types
    static final int WALL   = 1;
    static final int PELLET = 2;  // fuel cell
    static final int POWER  = 3;  // power cell (lead shield)
    static final int EMPTY  = 0;

    // ── Classic-style 19×19 maze (1=wall, 2=fuel, 3=power, 0=ghost house) ──
    static final int[][] BASE_MAZE = {
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1},
        {1,3,2,2,2,2,2,2,2,1,2,2,2,2,2,2,2,3,1},
        {1,2,1,1,2,1,1,1,2,1,2,1,1,1,2,1,1,2,1},
        {1,2,1,1,2,1,1,1,2,1,2,1,1,1,2,1,1,2,1},
        {1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1},
        {1,2,1,1,2,1,2,1,1,1,1,1,2,1,2,1,1,2,1},
        {1,2,2,2,2,1,2,2,2,1,2,2,2,1,2,2,2,2,1},
        {1,1,1,1,2,1,1,1,0,0,0,1,1,1,2,1,1,1,1},
        {0,0,0,1,2,1,0,0,0,0,0,0,0,1,2,1,0,0,0},
        {1,1,1,1,2,1,0,1,1,0,1,1,0,1,2,1,1,1,1},
        {0,0,0,0,2,0,0,1,0,0,0,1,0,0,2,0,0,0,0},
        {1,1,1,1,2,1,0,1,1,1,1,1,0,1,2,1,1,1,1},
        {0,0,0,1,2,1,0,0,0,0,0,0,0,1,2,1,0,0,0},
        {1,1,1,1,2,1,0,1,1,1,1,1,0,1,2,1,1,1,1},
        {1,2,2,2,2,2,2,2,2,1,2,2,2,2,2,2,2,2,1},
        {1,2,1,1,2,1,1,1,2,1,2,1,1,1,2,1,1,2,1},
        {1,3,2,1,2,2,2,2,2,2,2,2,2,2,2,1,2,3,1},
        {1,1,2,1,2,1,2,1,1,1,1,1,2,1,2,1,2,1,1},
        {1,2,2,2,2,2,2,2,2,1,2,2,2,2,2,2,2,2,1},
    };

    // ── Inner classes ────────────────────────────────────────────
    static class Ghost {
        double x, y;
        int    dir;   // 0=right 1=down 2=left 3=up
        Color  color;
        boolean scared;
        Ghost(double x, double y, Color color) {
            this.x = x; this.y = y; this.color = color; this.dir = (int)(Math.random()*4);
        }
        int col() { return (int)(x / CELL); }
        int row() { return (int)(y / CELL); }
    }

    // ── Game state ───────────────────────────────────────────────
    int[][] maze;
    int totalPellets;

    double px, py;          // player pixel position
    int    pTargetCol, pTargetRow;  // next grid cell the player is moving towards
    int    pDir = -1;        // current move direction (-1=stopped)
    int    pNextDir = -1;    // queued next direction

    int    score, lives;
    int    shieldTicks;      // invincibility from power cell
    boolean gameOver, won;

    List<Ghost> ghosts = new ArrayList<>();
    Random rng = new Random();
    Timer  timer;

    // ── Constructor ──────────────────────────────────────────────
    ReactorRun() {
        setPreferredSize(new Dimension(WIN_W, WIN_H));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        reset();
    }

    void reset() {
        maze = new int[ROWS_M][COLS];
        totalPellets = 0;
        for (int r = 0; r < ROWS_M; r++)
            for (int c = 0; c < COLS; c++) {
                maze[r][c] = BASE_MAZE[r][c];
                if (maze[r][c] == PELLET || maze[r][c] == POWER) totalPellets++;
            }

        // Player starts bottom-left open cell
        px = 1 * CELL + CELL / 2.0;
        py = 16 * CELL + CELL / 2.0;
        pTargetCol = 1; pTargetRow = 16;
        pDir = -1; pNextDir = -1;

        ghosts.clear();
        ghosts.add(new Ghost(9 * CELL + CELL / 2.0, 10 * CELL + CELL / 2.0, new Color(255,  80,  80)));
        ghosts.add(new Ghost(9 * CELL + CELL / 2.0, 10 * CELL + CELL / 2.0, new Color(255, 180, 180)));
        ghosts.add(new Ghost(9 * CELL + CELL / 2.0, 10 * CELL + CELL / 2.0, new Color(100, 200, 255)));
        ghosts.add(new Ghost(9 * CELL + CELL / 2.0, 10 * CELL + CELL / 2.0, new Color(255, 180,  50)));

        score = 0; lives = 3; shieldTicks = 0;
        gameOver = won = false;

        if (timer != null) timer.stop();
        timer = new Timer(40, e -> { update(); repaint(); });
        timer.start();
    }

    // Directions: 0=right 1=down 2=left 3=up
    static final int[] DX = { 1,  0, -1, 0};
    static final int[] DY = { 0,  1,  0,-1};

    boolean canMove(int row, int col, int dir) {
        int nr = row + DY[dir];
        int nc = col + DX[dir];
        if (nr < 0 || nr >= ROWS_M || nc < 0 || nc >= COLS) return false;
        return maze[nr][nc] != WALL;
    }

    // ── Game loop ────────────────────────────────────────────────
    void update() {
        if (gameOver || won) return;
        if (shieldTicks > 0) shieldTicks--;

        movePlayer();
        moveGhosts();
        checkCollisions();
    }

    void movePlayer() {
        double spd = 2.0;
        int curCol = (int)(px / CELL);
        int curRow = (int)(py / CELL);

        // Snap to grid centre when close
        double cx = curCol * CELL + CELL / 2.0;
        double cy = curRow * CELL + CELL / 2.0;
        boolean atCentre = Math.abs(px - cx) < spd + 0.5 && Math.abs(py - cy) < spd + 0.5;

        if (atCentre) {
            px = cx; py = cy;
            // Try queued direction first
            if (pNextDir >= 0 && canMove(curRow, curCol, pNextDir)) {
                pDir = pNextDir; pNextDir = -1;
            }
            // Continue current direction
            if (pDir >= 0 && !canMove(curRow, curCol, pDir)) pDir = -1;

            // Collect pellet/power cell
            int cell = maze[curRow][curCol];
            if (cell == PELLET) { maze[curRow][curCol] = EMPTY; score += 10; totalPellets--; }
            else if (cell == POWER)  { maze[curRow][curCol] = EMPTY; score += 50; totalPellets--;
                shieldTicks = 120;
                for (Ghost gh : ghosts) gh.scared = true; }

            if (totalPellets <= 0) { won = true; timer.stop(); return; }
        }

        if (pDir == 0) px += spd;
        else if (pDir == 1) py += spd;
        else if (pDir == 2) px -= spd;
        else if (pDir == 3) py -= spd;
    }

    void moveGhosts() {
        double spd = shieldTicks > 0 ? 1.0 : 1.5;
        for (Ghost g : ghosts) {
            int gr = g.row(), gc = g.col();
            double cx = gc * CELL + CELL / 2.0;
            double cy = gr * CELL + CELL / 2.0;
            boolean atCentre = Math.abs(g.x - cx) < spd + 0.5 && Math.abs(g.y - cy) < spd + 0.5;
            if (atCentre) {
                g.x = cx; g.y = cy;
                // Choose direction: prefer toward player, avoid reversing
                int pr2 = (int)(py / CELL), pc2 = (int)(px / CELL);
                int best = g.dir; double bestScore = -1e9;
                int[] dirs = {0, 1, 2, 3};
                for (int d : dirs) {
                    if (d == (g.dir + 2) % 4) continue; // no reverse
                    if (!canMove(gr, gc, d)) continue;
                    int nr = gr + DY[d], nc = gc + DX[d];
                    double score2 = shieldTicks > 0
                        ? (Math.abs(nr - pr2) + Math.abs(nc - pc2))     // flee
                        : -(Math.abs(nr - pr2) + Math.abs(nc - pc2));   // chase
                    if (score2 > bestScore || rng.nextInt(5) == 0) {
                        bestScore = score2; best = d;
                    }
                }
                if (!canMove(gr, gc, best)) {
                    // pick any valid direction
                    for (int d = 0; d < 4; d++) if (canMove(gr, gc, d)) { best = d; break; }
                }
                g.dir = best;
                g.scared = shieldTicks > 0;
            }
            g.x += DX[g.dir] * spd;
            g.y += DY[g.dir] * spd;
        }
    }

    void checkCollisions() {
        for (Ghost g : ghosts) {
            if (Math.abs(g.x - px) < CELL * 0.55 && Math.abs(g.y - py) < CELL * 0.55) {
                if (shieldTicks > 0) {
                    // Eat ghost
                    g.x = 9 * CELL + CELL / 2.0;
                    g.y = 10 * CELL + CELL / 2.0;
                    score += 200;
                } else {
                    lives--;
                    if (lives <= 0) { gameOver = true; timer.stop(); return; }
                    // Respawn player
                    px = 1 * CELL + CELL / 2.0; py = 16 * CELL + CELL / 2.0;
                    pDir = -1;
                }
            }
        }
    }

    // ── Rendering ────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Maze
        for (int r = 0; r < ROWS_M; r++) {
            for (int c = 0; c < COLS; c++) {
                int cell = maze[r][c];
                int x = c * CELL, y = r * CELL;
                if (cell == WALL) {
                    g2.setColor(new Color(0, 50, 160));
                    g2.fillRect(x, y, CELL, CELL);
                    g2.setColor(new Color(20, 80, 220));
                    g2.drawRect(x, y, CELL, CELL);
                } else if (cell == PELLET) {
                    g2.setColor(new Color(255, 200, 60));
                    g2.fillOval(x + CELL/2 - 4, y + CELL/2 - 4, 8, 8);
                } else if (cell == POWER) {
                    g2.setColor(new Color(100, 255, 180));
                    g2.fillOval(x + CELL/2 - 9, y + CELL/2 - 9, 18, 18);
                    g2.setColor(Color.WHITE);
                    g2.drawOval(x + CELL/2 - 9, y + CELL/2 - 9, 18, 18);
                }
            }
        }

        // Ghosts (radiation clouds)
        for (Ghost gh : ghosts) drawGhost(g2, gh);

        // Player (maintenance drone)
        drawDrone(g2, (int)px, (int)py);

        // HUD below maze
        int hudY = ROWS_M * CELL + 15;
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        g2.drawString("REACTOR RUN", 10, hudY);
        g2.drawString("Score: " + score, 160, hudY);
        g2.drawString("Lives: " + lives, 320, hudY);
        if (shieldTicks > 0) {
            g2.setColor(new Color(100, 255, 180));
            g2.drawString("SHIELD ACTIVE", 420, hudY);
        }
        g2.setColor(new Color(140, 140, 180));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g2.drawString("Arrow Keys to move   R = restart", 10, hudY + 22);

        if (gameOver) drawOverlay(g2, "CONTAINMENT BREACH!", new Color(255, 70, 70));
        else if (won)  drawOverlay(g2, "REACTOR PURGED! MISSION COMPLETE!", new Color(0, 255, 160));
    }

    void drawDrone(Graphics2D g2, int x, int y) {
        Color body = shieldTicks > 0 ? new Color(100, 255, 180) : new Color(60, 200, 255);
        g2.setColor(body);
        g2.fillOval(x - 13, y - 13, 26, 26);
        g2.setColor(body.darker());
        g2.drawOval(x - 13, y - 13, 26, 26);
        g2.setColor(Color.WHITE);
        g2.fillOval(x - 5, y - 5, 10, 10);
    }

    void drawGhost(Graphics2D g2, Ghost gh) {
        int x = (int)gh.x - 13, y = (int)gh.y - 14;
        Color body = gh.scared ? new Color(60, 100, 255) : gh.color;
        g2.setColor(body);
        // Round top + rectangular body
        g2.fillArc(x, y, 26, 24, 0, 180);
        g2.fillRect(x, y + 12, 26, 14);
        // Wavy bottom
        g2.setColor(Color.BLACK);
        for (int i = 0; i < 3; i++) g2.fillOval(x + i * 9, y + 22, 9, 8);
        // Eyes
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 5, y + 4, 8, 8);
        g2.fillOval(x + 14, y + 4, 8, 8);
        g2.setColor(gh.scared ? Color.WHITE : new Color(0, 60, 180));
        g2.fillOval(x + 7, y + 6, 4, 4);
        g2.fillOval(x + 16, y + 6, 4, 4);
    }

    void drawOverlay(Graphics2D g2, String msg, Color col) {
        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRoundRect(30, ROWS_M * CELL / 2 - 40, WIN_W - 60, 90, 16, 16);
        g2.setColor(col);
        g2.setFont(new Font("Monospaced", Font.BOLD, 18));
        g2.drawString(msg, 50, ROWS_M * CELL / 2);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g2.drawString("Score: " + score + "   Press R to restart", 50, ROWS_M * CELL / 2 + 30);
    }

    // ── Key handling ─────────────────────────────────────────────
    @Override public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_RIGHT -> pNextDir = 0;
            case KeyEvent.VK_DOWN  -> pNextDir = 1;
            case KeyEvent.VK_LEFT  -> pNextDir = 2;
            case KeyEvent.VK_UP    -> pNextDir = 3;
            case KeyEvent.VK_R     -> reset();
        }
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    // ── Entry point ──────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Reactor Run");
            ReactorRun g = new ReactorRun();
            f.add(g); f.pack();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            g.requestFocusInWindow();
        });
    }
}
