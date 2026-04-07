import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * FUSION REACTOR GAME DEMOS – Main Launcher
 * ──────────────────────────────────────────
 * Displays a menu of all seven nuclear-themed arcade game demos.
 * Click a button to open that demo in its own window.
 */
public class Launcher extends JFrame {

    // ── Game entries: {title, class, description} ─────────────────
    static final String[][] GAMES = {
        {
            "Q-Fusion",
            "QFusion",
            "Q-Bert  →  Hop across an isometric reactor lattice\n" +
            "to stabilize fusion nodes. Avoid neutron bursts."
        },
        {
            "Fusion Field",
            "FusionField",
            "Asteroids  →  Pilot a maintenance craft inside a\n" +
            "failing tokamak. Destroy plasma fragments before\n" +
            "the heat meter triggers a meltdown."
        },
        {
            "Reactor Run",
            "ReactorRun",
            "Pac-Man  →  Collect fuel cells in a damaged fusion\n" +
            "facility. Radiation clouds chase you; power cells\n" +
            "grant temporary lead shielding."
        },
        {
            "Isotope Crossing",
            "IsotopeCrossing",
            "Frogger  →  Transport isotopes across conveyor belts\n" +
            "and coolant streams. Ride maintenance drones to\n" +
            "reach the containment chambers."
        },
        {
            "Neutron Defenders",
            "NeutronDefenders",
            "Space Invaders  →  Defend the fusion core from\n" +
            "descending neutron clusters. Switch between three\n" +
            "weapon modes: Pulse, Coolant, Beam."
        },
        {
            "Meltdown Climb",
            "MeltdownClimb",
            "Donkey Kong  →  Climb a reactor tower before\n" +
            "meltdown! Dodge radioactive waste drums and\n" +
            "plasma bolts from the sabotage robot."
        },
        {
            "Fusion Swarm",
            "FusionSwarm",
            "Galaga  →  Ion swarms dive-bomb your maintenance\n" +
            "craft. Survive 5 waves; rescue a captured ship\n" +
            "to unlock dual firepower."
        }
    };

    // ── UI colours ────────────────────────────────────────────────
    static final Color BG        = new Color(6, 8, 28);
    static final Color CARD_BG   = new Color(12, 20, 50);
    static final Color CARD_HL   = new Color(0, 50, 90);
    static final Color ACCENT    = new Color(0, 210, 180);
    static final Color BTN_BG    = new Color(0, 120, 100);
    static final Color BTN_HOV   = new Color(0, 180, 150);
    static final Color BTN_FG    = Color.WHITE;

    Launcher() {
        super("Fusion Reactor Game Demos – Launcher");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        buildUI();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);

        // ── Header ──────────────────────────────────────────────
        JPanel header = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Glow lines
                g2.setColor(new Color(0, 150, 130, 40));
                for (int i = 0; i < 8; i++) g2.drawLine(0, i * 12, getWidth(), i * 12);
                g2.setColor(ACCENT);
                g2.setFont(new Font("Monospaced", Font.BOLD, 26));
                g2.drawString("⚛  FUSION REACTOR GAME DEMOS  ⚛", 40, 52);
                g2.setColor(new Color(180, 220, 255));
                g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
                g2.drawString("Seven nuclear-themed arcade game demos. Click any title to play.", 55, 78);
            }
        };
        header.setPreferredSize(new Dimension(780, 95));
        header.setBackground(new Color(4, 8, 30));

        // ── Game cards grid ─────────────────────────────────────
        JPanel grid = new JPanel(new GridLayout(0, 2, 12, 12));
        grid.setBackground(BG);
        grid.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));

        for (String[] g : GAMES) {
            grid.add(buildCard(g[0], g[1], g[2]));
        }
        // Fill last cell if odd number
        if (GAMES.length % 2 != 0) {
            JPanel empty = new JPanel();
            empty.setBackground(BG);
            grid.add(empty);
        }

        root.add(header, BorderLayout.NORTH);
        root.add(grid, BorderLayout.CENTER);

        // ── Footer ──────────────────────────────────────────────
        JLabel footer = new JLabel("  Each game is a standalone Swing demo – R to restart in any game.", JLabel.LEFT);
        footer.setForeground(new Color(100, 120, 160));
        footer.setFont(new Font("Monospaced", Font.PLAIN, 11));
        footer.setBackground(new Color(4, 6, 22));
        footer.setOpaque(true);
        footer.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
    }

    JPanel buildCard(String title, String className, String desc) {
        JPanel card = new JPanel(new BorderLayout(6, 6)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(new Color(0, 100, 90, 60));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // Title label
        JLabel titleLbl = new JLabel(title);
        titleLbl.setForeground(ACCENT);
        titleLbl.setFont(new Font("Monospaced", Font.BOLD, 15));

        // Description
        JTextArea descArea = new JTextArea(desc);
        descArea.setEditable(false);
        descArea.setFocusable(false);
        descArea.setBackground(new Color(0, 0, 0, 0));
        descArea.setForeground(new Color(190, 200, 220));
        descArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        descArea.setOpaque(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);

        // Play button
        JButton playBtn = new JButton("▶  Play " + title) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? BTN_HOV : BTN_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(getModel().isRollover() ? Color.WHITE : new Color(200, 255, 240));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                super.paintComponent(g);
            }
        };
        playBtn.setForeground(BTN_FG);
        playBtn.setFont(new Font("Monospaced", Font.BOLD, 12));
        playBtn.setFocusPainted(false);
        playBtn.setContentAreaFilled(false);
        playBtn.setBorderPainted(false);
        playBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        playBtn.addActionListener(e -> launchGame(className, title));

        card.add(titleLbl,  BorderLayout.NORTH);
        card.add(descArea,  BorderLayout.CENTER);
        card.add(playBtn,   BorderLayout.SOUTH);
        return card;
    }

    void launchGame(String className, String title) {
        try {
            Class<?> cls = Class.forName(className);
            JPanel gamePanel = (JPanel) cls.getDeclaredConstructor().newInstance();

            JFrame gf = new JFrame(title);
            gf.add(gamePanel);
            gf.pack();
            gf.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            gf.setLocationRelativeTo(this);
            gf.setVisible(true);
            gamePanel.requestFocusInWindow();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Could not launch " + title + ":\n" + ex.getMessage(),
                "Launch Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Launcher::new);
    }
}
