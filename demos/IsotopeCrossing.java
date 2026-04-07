import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * ISOTOPE CROSSING – Frogger-style isotope delivery demo
 * ──────────────────────────────────────────────────────
 * Guide a lab assistant across a hazardous reactor complex.
 * Dodge conveyor belts and coolant streams, then ride
 * maintenance drones across to reach the containment chambers.
 *
 * Controls:  Arrow keys to hop   R = restart
 */
public class IsotopeCrossing extends JPanel implements KeyListener {

    static final int CELL  = 50;    // pixel size of one grid cell
    static final int COLS  = 14;    // grid columns
    static final int LANES = 13;    // total rows (0 = goal row, 12 = start)
    static final int WIN_W = COLS * CELL;
    static final int WIN_H = LANES * CELL + 60;

    // Lane types
    static final int SAFE     = 0;
    static final int OBSTACLE = 1;  // hazardous conveyor / coolant
    static final int PLATFORM = 2;  // rideable drone / shield platform

    // ── Lane definitions [type, dir(1=right,-1=left), speed, obstacle-width] ─
    // Row 0 = goal, row 12 = start
    static final int[][] LANE_DEF = {
        // type        dir  speed  objW
        { SAFE,        0,   0,     0  },  // row 0: containment chambers
        { PLATFORM,   -1,   2,     70 },  // row 1: slow drones left
        { PLATFORM,    1,   3,     90 },  // row 2: fast drones right
        { PLATFORM,   -1,   2,     80 },  // row 3: medium drones left
        { SAFE,        0,   0,     0  },  // row 4: mid-safe zone
        { OBSTACLE,   -1,   4,     50 },  // row 5: fast coolant left
        { OBSTACLE,    1,   2,     60 },  // row 6: slow conveyor right
        { OBSTACLE,   -1,   3,     55 },  // row 7: medium coolant left
        { OBSTACLE,    1,   4,     45 },  // row 8: fast conveyor right
        { OBSTACLE,   -1,   2,     65 },  // row 9: slow coolant left
        { SAFE,        0,   0,     0  },  // row 10: lower safe zone (unused)
        { OBSTACLE,    1,   3,     55 },  // row 11: conveyor right
        { SAFE,        0,   0,     0  },  // row 12: start
    };

    // Number of obstacles/platforms per lane
    static final int[] LANE_COUNT = {0, 3, 2, 3, 0, 3, 3, 3, 3, 3, 0, 3, 0};

    // Goal slot positions (column indices for the 5 chambers)
    static final int[] GOAL_COLS = {1, 3, 6, 9, 12};
    boolean[] goalFilled = new boolean[5];

    // ── Inner class: lane object ──────────────────────────────────
    static class LaneObj {
        double x;
        int    lane, w, h, type; // type matches OBSTACLE/PLATFORM
        LaneObj(double x, int lane, int w, int h, int type) {
            this.x = x; this.lane = lane; this.w = w; this.h = h; this.type = type;
        }
    }

    // ── Game state ───────────────────────────────────────────────
    List<LaneObj> laneObjs = new ArrayList<>();
    int  playerCol, playerRow;
    boolean playerOnPlatform;
    double playerRideX;       // extra x offset when riding a platform
    int  score, lives;
    boolean gameOver, won;
    int tick;
    Timer timer;

    IsotopeCrossing() {
        setPreferredSize(new Dimension(WIN_W, WIN_H));
        setBackground(new Color(10, 20, 35));
        setFocusable(true);
        addKeyListener(this);
        reset();
    }

    void reset() {
        laneObjs.clear();
        Random rng = new Random(42); // fixed seed for reproducibility
        for (int lane = 0; lane < LANES; lane++) {
            int type = LANE_DEF[lane][0];
            if (type == SAFE) continue;
            int count = LANE_COUNT[lane];
            int objW  = LANE_DEF[lane][3];
            double spacing = (double) WIN_W / count;
            for (int i = 0; i < count; i++) {
                double startX = i * spacing + rng.nextInt((int)spacing / 3);
                laneObjs.add(new LaneObj(startX, lane, objW, CELL - 8, type));
            }
        }

        playerCol = COLS / 2;
        playerRow = LANES - 1;
        playerOnPlatform = false;
        playerRideX = 0;
        goalFilled = new boolean[5];
        score = 0; lives = 3; gameOver = won = false; tick = 0;

        if (timer != null) timer.stop();
        timer = new Timer(16, e -> { update(); repaint(); });
        timer.start();
    }

    void update() {
        if (gameOver || won) return;
        tick++;

        // Move lane objects
        for (LaneObj obj : laneObjs) {
            double spd = LANE_DEF[obj.lane][2] * LANE_DEF[obj.lane][1];
            obj.x += spd;
            // Wrap around
            if (obj.x > WIN_W + obj.w) obj.x = -obj.w;
            if (obj.x < -obj.w) obj.x = WIN_W + obj.w;
        }

        // Check if player is in the platform rows
        int laneType = LANE_DEF[playerRow][0];
        if (laneType == PLATFORM) {
            // Find platform under player
            LaneObj under = platformUnder();
            if (under != null) {
                // Ride it
                double spd = LANE_DEF[under.lane][2] * LANE_DEF[under.lane][1];
                playerRideX += spd;
                // Check if carried off-screen
                double px = playerCol * CELL + CELL / 2.0 + playerRideX;
                if (px < 0 || px > WIN_W) {
                    loseLife(); return;
                }
            } else {
                // No platform: drown/fall
                loseLife(); return;
            }
        } else {
            playerRideX = 0;
        }

        // Check obstacle collision (not on platform rows)
        if (laneType == OBSTACLE) {
            if (hitsObstacle()) { loseLife(); return; }
        }
    }

    LaneObj platformUnder() {
        double px = playerCol * CELL + CELL / 2.0 + playerRideX;
        for (LaneObj obj : laneObjs) {
            if (obj.lane != playerRow) continue;
            if (px >= obj.x && px <= obj.x + obj.w) return obj;
        }
        return null;
    }

    boolean hitsObstacle() {
        double px = playerCol * CELL + CELL / 2.0;
        double py2 = playerRow * CELL + CELL / 2.0;
        for (LaneObj obj : laneObjs) {
            if (obj.lane != playerRow) continue;
            if (px > obj.x - 8 && px < obj.x + obj.w + 8 &&
                py2 > obj.lane * CELL + 4 && py2 < (obj.lane + 1) * CELL - 4)
                return true;
        }
        return false;
    }

    void loseLife() {
        lives--;
        if (lives <= 0) { gameOver = true; timer.stop(); return; }
        playerCol = COLS / 2; playerRow = LANES - 1;
        playerRideX = 0;
    }

    void hop(int dRow, int dCol) {
        if (gameOver || won) return;

        // Adjust col for platform drift
        if (LANE_DEF[playerRow][0] == PLATFORM) {
            playerCol = (int)((playerCol * CELL + CELL / 2.0 + playerRideX) / CELL);
        }
        playerRideX = 0;

        int nr = playerRow + dRow;
        int nc = playerCol + dCol;
        if (nr < 0 || nr >= LANES || nc < 0 || nc >= COLS) return;

        playerRow = nr;
        playerCol = nc;

        // Check goal row
        if (playerRow == 0) {
            for (int i = 0; i < GOAL_COLS.length; i++) {
                if (Math.abs(playerCol - GOAL_COLS[i]) <= 1 && !goalFilled[i]) {
                    goalFilled[i] = true;
                    score += 200;
                    playerCol = COLS / 2; playerRow = LANES - 1;
                    playerRideX = 0;
                    // Win check
                    boolean allFilled = true;
                    for (boolean b : goalFilled) if (!b) { allFilled = false; break; }
                    if (allFilled) { won = true; timer.stop(); }
                    return;
                }
            }
            // Didn't land on a goal slot
            loseLife();
        }
    }

    // ── Rendering ────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw lane backgrounds
        for (int lane = 0; lane < LANES; lane++) {
            int y = lane * CELL;
            int type = LANE_DEF[lane][0];
            Color bg = type == OBSTACLE ? new Color(60, 20, 20) :
                       type == PLATFORM ? new Color(15, 45, 60) :
                                          new Color(20, 45, 20);
            g2.setColor(bg);
            g2.fillRect(0, y, WIN_W, CELL);
            g2.setColor(new Color(0, 0, 0, 80));
            g2.drawLine(0, y + CELL - 1, WIN_W, y + CELL - 1);
        }

        // Goal row: containment chambers
        for (int i = 0; i < GOAL_COLS.length; i++) {
            int cx = GOAL_COLS[i] * CELL;
            g2.setColor(goalFilled[i] ? new Color(0, 200, 100) : new Color(40, 100, 80));
            g2.fillRoundRect(cx + 4, 4, CELL - 8, CELL - 8, 8, 8);
            g2.setColor(goalFilled[i] ? Color.WHITE : new Color(80, 180, 140));
            g2.setFont(new Font("Monospaced", Font.BOLD, 9));
            g2.drawString(goalFilled[i] ? "SEALED" : "CHAMBER", cx + 3, CELL / 2 + 3);
        }

        // Draw lane objects
        for (LaneObj obj : laneObjs) {
            int ox = (int) obj.x;
            int oy = obj.lane * CELL + 4;
            if (obj.type == OBSTACLE) {
                // Conveyor / coolant hazard
                g2.setColor(new Color(200, 60, 30));
                g2.fillRoundRect(ox, oy, obj.w, obj.h, 6, 6);
                g2.setColor(new Color(255, 120, 80));
                g2.drawRoundRect(ox, oy, obj.w, obj.h, 6, 6);
                // Arrow showing direction
                int dir = LANE_DEF[obj.lane][1];
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Monospaced", Font.BOLD, 14));
                g2.drawString(dir > 0 ? ">>>" : "<<<", ox + 5, oy + obj.h / 2 + 5);
            } else {
                // Platform drone
                g2.setColor(new Color(30, 130, 180));
                g2.fillRoundRect(ox, oy, obj.w, obj.h, 10, 10);
                g2.setColor(new Color(80, 200, 255));
                g2.drawRoundRect(ox, oy, obj.w, obj.h, 10, 10);
                // Drone lights
                g2.setColor(new Color(0, 255, 200));
                g2.fillOval(ox + 6, oy + obj.h / 2 - 4, 8, 8);
                g2.fillOval(ox + obj.w - 14, oy + obj.h / 2 - 4, 8, 8);
            }
        }

        // Draw player
        double drawX = playerCol * CELL + CELL / 2.0 + playerRideX;
        double drawY = playerRow * CELL + CELL / 2.0;
        g2.setColor(new Color(255, 220, 100));
        g2.fillOval((int)drawX - 14, (int)drawY - 14, 28, 28);
        g2.setColor(new Color(200, 150, 0));
        g2.drawOval((int)drawX - 14, (int)drawY - 14, 28, 28);
        // Isotope symbol
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        g2.drawString("ISO", (int)drawX - 13, (int)drawY + 5);

        // HUD
        int hudY = LANES * CELL + 18;
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        g2.drawString("ISOTOPE CROSSING", 10, hudY);
        g2.drawString("Score: " + score, 210, hudY);
        g2.drawString("Lives: " + lives, 360, hudY);
        g2.setColor(new Color(140, 140, 180));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g2.drawString("Arrow keys to hop   R = restart   Goal: fill all 5 chambers", 10, hudY + 22);

        if (gameOver) drawOverlay(g2, "ISOTOPE LOST – MELTDOWN!", new Color(255, 70, 70));
        else if (won)  drawOverlay(g2, "ALL ISOTOPES DELIVERED!", new Color(0, 255, 160));
    }

    void drawOverlay(Graphics2D g2, String msg, Color col) {
        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRoundRect(50, LANES * CELL / 2 - 35, WIN_W - 100, 80, 14, 14);
        g2.setColor(col);
        g2.setFont(new Font("Monospaced", Font.BOLD, 18));
        g2.drawString(msg, 70, LANES * CELL / 2 + 5);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g2.drawString("Score: " + score + "   Press R to restart", 70, LANES * CELL / 2 + 30);
    }

    // ── Key handling ─────────────────────────────────────────────
    @Override public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP    -> hop(-1,  0);
            case KeyEvent.VK_DOWN  -> hop( 1,  0);
            case KeyEvent.VK_LEFT  -> hop( 0, -1);
            case KeyEvent.VK_RIGHT -> hop( 0,  1);
            case KeyEvent.VK_R     -> reset();
        }
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    // ── Entry point ──────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Isotope Crossing");
            IsotopeCrossing g = new IsotopeCrossing();
            f.add(g); f.pack();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            g.requestFocusInWindow();
        });
    }
}
