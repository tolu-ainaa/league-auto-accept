package autoaccept;

import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Hextech-styled window that watches the League client and accepts the
 * ready check ("match found" popup) the moment it appears.
 */
public class App {

    // Hextech palette, lifted from the League client's visual language.
    static final Color BG_DARK    = new Color(0x010A13);
    static final Color BG_LIGHT   = new Color(0x0A1428);
    static final Color GOLD       = new Color(0xC8AA6E);
    static final Color GOLD_DARK  = new Color(0x785A28);
    static final Color GOLD_PALE  = new Color(0xF0E6D2);
    static final Color TEAL       = new Color(0x0AC8B9);
    static final Color BLUE       = new Color(0x2A9FD8);
    static final Color RED        = new Color(0xBE1E37);
    static final Color TEXT_MUTED = new Color(0xA09B8C);

    static final String SUPPORT_URL = "https://ko-fi.com/tolu_aina";

    private final JFrame frame = new JFrame("League Auto Accept");
    private final JLabel statusLabel = new JLabel("STARTING...", SwingConstants.CENTER);
    private final JLabel detailLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel countLabel = new JLabel("MATCHES ACCEPTED — 0", SwingConstants.CENTER);
    private final HexBadge badge = new HexBadge();
    private final HextechToggle toggle = new HextechToggle();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "lcu-poller");
                t.setDaemon(true);
                return t;
            });

    private LcuClient lcu;
    private int acceptedCount;
    private volatile boolean enabled = true;
    private TrayIcon trayIcon;
    private CheckboxMenuItem trayToggle;
    private boolean trayNoticeShown;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new App().start());
    }

    private void start() {
        buildUi();
        buildTray();
        scheduler.scheduleWithFixedDelay(this::tick, 0, 1, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        statusLabel.setFont(withTracking(new Font("Georgia", Font.BOLD, 17), 0.06f));
        statusLabel.setForeground(GOLD_PALE);
        detailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        detailLabel.setForeground(TEXT_MUTED);
        countLabel.setFont(withTracking(new Font("Segoe UI", Font.PLAIN, 10), 0.2f));
        countLabel.setForeground(TEXT_MUTED);

        toggle.addActionListener(e -> setAutoAccept(toggle.isSelected()));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        badge.setAlignmentX(0.5f);
        statusLabel.setAlignmentX(0.5f);
        detailLabel.setAlignmentX(0.5f);
        center.add(Box.createVerticalStrut(4));
        center.add(badge);
        center.add(Box.createVerticalStrut(12));
        center.add(statusLabel);
        center.add(Box.createVerticalStrut(6));
        center.add(detailLabel);

        JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        toggleRow.setOpaque(false);
        toggleRow.add(toggle);

        JLabel support = new JLabel("♥ SUPPORT THE DEV", SwingConstants.CENTER);
        support.setFont(withTracking(new Font("Segoe UI", Font.PLAIN, 10), 0.2f));
        support.setForeground(TEXT_MUTED);
        support.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        support.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { support.setForeground(GOLD_PALE); }
            @Override public void mouseExited(MouseEvent e) { support.setForeground(TEXT_MUTED); }
            @Override public void mouseClicked(MouseEvent e) { openSupportPage(); }
        });

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 0, 14, 0));
        countLabel.setAlignmentX(0.5f);
        support.setAlignmentX(0.5f);
        bottom.add(toggleRow);
        bottom.add(Box.createVerticalStrut(10));
        bottom.add(countLabel);
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(support);

        JPanel root = new HextechPanel();
        root.setLayout(new BorderLayout());
        root.add(buildTitleBar(), BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        frame.setUndecorated(true);
        frame.setContentPane(root);
        frame.setSize(420, 340);
        frame.setResizable(false);
        frame.setLocation(140, 140);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(buildIcon());
        frame.setVisible(true);
    }

    private JPanel buildTitleBar() {
        JLabel title = new JLabel("LEAGUE AUTO ACCEPT");
        title.setFont(withTracking(new Font("Georgia", Font.BOLD, 12), 0.28f));
        title.setForeground(GOLD);

        JLabel minimize = titleButton("—");
        minimize.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { frame.setState(Frame.ICONIFIED); }
        });
        JLabel close = titleButton("✕");
        close.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { hideToTray(); }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttons.setOpaque(false);
        buttons.add(minimize);
        buttons.add(close);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 12));
        bar.add(title, BorderLayout.WEST);
        bar.add(buttons, BorderLayout.EAST);

        // The window is undecorated, so the title bar handles dragging.
        Point[] grab = new Point[1];
        bar.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { grab[0] = e.getPoint(); }
        });
        bar.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                frame.setLocation(e.getXOnScreen() - grab[0].x - bar.getX(),
                                  e.getYOnScreen() - grab[0].y - bar.getY());
            }
        });
        return bar;
    }

    private void buildTray() {
        if (!SystemTray.isSupported()) return;

        MenuItem open = new MenuItem("Open");
        open.addActionListener(e -> showWindow());
        trayToggle = new CheckboxMenuItem("Auto-accept", true);
        trayToggle.addItemListener(e -> setAutoAccept(trayToggle.getState()));
        MenuItem support = new MenuItem("Support the dev ♥");
        support.addActionListener(e -> openSupportPage());
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(e -> System.exit(0));

        PopupMenu menu = new PopupMenu();
        menu.add(open);
        menu.add(trayToggle);
        menu.add(support);
        menu.addSeparator();
        menu.add(exit);

        trayIcon = new TrayIcon(buildIcon(), "League Auto Accept", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> showWindow());  // double-click
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            trayIcon = null;
        }
    }

    private void openSupportPage() {
        try {
            Desktop.getDesktop().browse(URI.create(SUPPORT_URL));
        } catch (Exception ignored) {
            // No default browser configured; nothing sensible to do.
        }
    }

    private void showWindow() {
        frame.setVisible(true);
        frame.setState(Frame.NORMAL);
        frame.toFront();
    }

    /** Close button: keep watching from the tray rather than exiting. */
    private void hideToTray() {
        if (trayIcon == null) {
            System.exit(0);
        }
        frame.setVisible(false);
        if (!trayNoticeShown) {
            trayNoticeShown = true;
            trayIcon.displayMessage("Still watching your queue",
                    "League Auto Accept is running in the tray. Right-click the icon to exit.",
                    TrayIcon.MessageType.INFO);
        }
    }

    /** Single source of truth for the on/off state; keeps window and tray in sync. */
    private void setAutoAccept(boolean on) {
        enabled = on;
        toggle.setSelected(on);
        toggle.setText(on ? "AUTO-ACCEPT: ON" : "AUTO-ACCEPT: OFF");
        toggle.setForeground(on ? GOLD : TEXT_MUTED);
        if (trayToggle != null) trayToggle.setState(on);
    }

    private JLabel titleButton(String glyph) {
        JLabel label = new JLabel(glyph);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(TEXT_MUTED);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { label.setForeground(GOLD_PALE); }
            @Override public void mouseExited(MouseEvent e) { label.setForeground(TEXT_MUTED); }
        });
        return label;
    }

    private static BufferedImage buildIcon() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(BG_DARK);
        g2.fill(hexPath(16, 16, 14));
        g2.setColor(GOLD);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(hexPath(16, 16, 13));
        g2.setColor(TEAL);
        g2.fill(hexPath(16, 16, 5));
        g2.dispose();
        return img;
    }

    private static Font withTracking(Font font, float tracking) {
        Map<TextAttribute, Object> attrs = new HashMap<>();
        attrs.put(TextAttribute.TRACKING, tracking);
        return font.deriveFont(attrs);
    }

    private static Path2D hexPath(double cx, double cy, double r) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i - 90);
            double x = cx + r * Math.cos(a), y = cy + r * Math.sin(a);
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        return p;
    }

    /** Root panel: dark gradient, double gold frame, corner filigree. */
    private static class HextechPanel extends JPanel {
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            g2.setPaint(new GradientPaint(0, 0, BG_LIGHT, 0, h, BG_DARK));
            g2.fillRect(0, 0, w, h);

            g2.setColor(GOLD_DARK);
            g2.drawRect(0, 0, w - 1, h - 1);
            g2.setColor(new Color(GOLD_DARK.getRed(), GOLD_DARK.getGreen(), GOLD_DARK.getBlue(), 90));
            g2.drawRect(4, 4, w - 9, h - 9);

            g2.setColor(GOLD);
            g2.setStroke(new BasicStroke(2f));
            int len = 18;
            g2.drawLine(1, 1, 1 + len, 1);          g2.drawLine(1, 1, 1, 1 + len);
            g2.drawLine(w - 2, 1, w - 2 - len, 1);  g2.drawLine(w - 2, 1, w - 2, 1 + len);
            g2.drawLine(1, h - 2, 1 + len, h - 2);  g2.drawLine(1, h - 2, 1, h - 2 - len);
            g2.drawLine(w - 2, h - 2, w - 2 - len, h - 2);
            g2.drawLine(w - 2, h - 2, w - 2, h - 2 - len);
            g2.dispose();
        }
    }

    /** Pulsing hexagonal "hextech crystal" that shows the current state by color. */
    private static class HexBadge extends JComponent {
        private volatile Color glow = TEXT_MUTED;
        private double phase;

        HexBadge() {
            Dimension size = new Dimension(120, 104);
            setPreferredSize(size);
            setMaximumSize(size);
            new javax.swing.Timer(40, e -> { phase += 0.12; repaint(); }).start();
        }

        void setGlow(Color color) { glow = color; }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
            double pulse = 0.55 + 0.45 * Math.sin(phase);

            for (int i = 5; i >= 1; i--) {
                g2.setColor(new Color(glow.getRed() / 255f, glow.getGreen() / 255f,
                        glow.getBlue() / 255f, (float) (pulse * 0.10)));
                g2.setStroke(new BasicStroke(i * 3f));
                g2.draw(hexPath(cx, cy, 34));
            }

            Path2D hex = hexPath(cx, cy, 34);
            g2.setPaint(new GradientPaint(0, (float) (cy - 34), BG_LIGHT, 0, (float) (cy + 34), BG_DARK));
            g2.fill(hex);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(GOLD);
            g2.draw(hex);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(GOLD_DARK);
            g2.draw(hexPath(cx, cy, 28));

            g2.setColor(glow);
            g2.fill(hexPath(cx, cy, 9 + 4 * pulse));
            g2.dispose();
        }
    }

    /** Gold-framed toggle in the style of the client's "Find Match" buttons. */
    private static class HextechToggle extends JToggleButton {
        HextechToggle() {
            super("AUTO-ACCEPT: ON", true);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setForeground(GOLD);
            setFont(withTracking(new Font("Georgia", Font.BOLD, 13), 0.12f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(210, 40));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            g2.setPaint(new GradientPaint(0, 0, new Color(0x1E282D), 0, h, new Color(0x0A1014)));
            g2.fillRect(1, 1, w - 2, h - 2);
            g2.setColor(getModel().isRollover() ? GOLD_PALE : GOLD_DARK);
            g2.drawRect(0, 0, w - 1, h - 1);
            g2.setColor(new Color(GOLD_DARK.getRed(), GOLD_DARK.getGreen(), GOLD_DARK.getBlue(), 120));
            g2.drawRect(2, 2, w - 5, h - 5);
            if (isSelected()) {
                g2.setColor(TEAL);
                g2.fillRect(4, h - 6, w - 8, 2);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------------------------------------------------------------- logic

    /** Runs on the poller thread once a second. */
    private void tick() {
        if (lcu == null) {
            lcu = LcuClient.discover();
            if (lcu == null) {
                setStatus("Waiting for League client...",
                        "Start the League client and I'll connect automatically.", TEXT_MUTED);
                return;
            }
        }

        String phase = lcu.get("/lol-gameflow/v1/gameflow-phase");
        if (phase == null) {
            lcu = null;
            setStatus("Lost connection to client",
                    "Reconnecting when the League client is back...", RED);
            return;
        }
        phase = phase.replace("\"", "").trim();

        if (phase.equals("ReadyCheck")) {
            String readyCheck = lcu.get("/lol-matchmaking/v1/ready-check");
            boolean notAnsweredYet =
                    readyCheck != null && readyCheck.contains("\"playerResponse\":\"None\"");

            if (enabled && notAnsweredYet) {
                if (lcu.post("/lol-matchmaking/v1/ready-check/accept")) {
                    acceptedCount++;
                    setStatus("Match found — accepted!",
                            "Get back to your PC, champ select is starting.", TEAL);
                    if (trayIcon != null) {
                        trayIcon.displayMessage("Match found!",
                                "Accepted it for you — champ select is starting.",
                                TrayIcon.MessageType.INFO);
                    }
                    return;
                }
            }
            setStatus("Match found",
                    enabled ? "Already answered." : "Auto-accept is off — accept it yourself!", BLUE);
            return;
        }

        switch (phase) {
            case "Matchmaking" -> setStatus("In queue...",
                    enabled ? "Go grab your snack — I've got this." : "Auto-accept is OFF.", BLUE);
            case "Lobby" -> setStatus("In lobby", "Waiting for you to start queue.", GOLD);
            case "ChampSelect" -> setStatus("In champ select", "Pick your champion!", TEAL);
            case "InProgress" -> setStatus("Game in progress", "Good luck, have fun.", TEAL);
            case "WaitingForStats", "PreEndOfGame", "EndOfGame" ->
                    setStatus("Game finished", "Waiting for the next queue.", TEXT_MUTED);
            default -> setStatus("Connected to League client", "Not in a lobby yet.", TEXT_MUTED);
        }
    }

    private void setStatus(String status, String detail, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status.toUpperCase());
            statusLabel.setForeground(color == TEXT_MUTED ? GOLD_PALE : color);
            detailLabel.setText(detail);
            countLabel.setText("MATCHES ACCEPTED — " + acceptedCount);
            badge.setGlow(color);
            if (trayIcon != null) trayIcon.setToolTip("League Auto Accept — " + status);
        });
    }
}
