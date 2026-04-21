package me.zed_0xff.zombie_buddy;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone entry point for a non-headless JVM: shows one Swing window listing
 * all Java mods that need approval. Invoked by {@link Loader} via ProcessBuilder.
 *
 * <p>Args: {@code <requestFile> <responseFile>}
 */
public final class BatchJarApprovalMain {

    private static final Color ZBS_ROW_OK = new Color(220, 255, 220);
    private static final Color ZBS_ROW_BAD = new Color(255, 210, 210);

    private BatchJarApprovalMain() {}

    public static void main(String[] args) {
        if (args == null || args.length != 2) {
            System.err.println("Usage: BatchJarApprovalMain <requestFile> <responseFile>");
            System.exit(2);
        }
        Path req = Paths.get(args[0]);
        Path resp = Paths.get(args[1]);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        try {
            final List<JarBatchApprovalProtocol.Entry> entries = JarBatchApprovalProtocol.readRequest(req);
            if (entries.isEmpty()) {
                JarBatchApprovalProtocol.writeResponse(resp, new ArrayList<>());
                System.exit(0);
                return;
            }

            SwingUtilities.invokeLater(() -> showDialog(entries, resp));
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(2);
        }
    }

    private static String modTitle(JarBatchApprovalProtocol.Entry e) {
        String d = e.modDisplayName;
        if (d != null && !d.trim().isEmpty()) {
            return d;
        }
        return e.modId != null && !e.modId.isEmpty() ? e.modId : e.modKey;
    }

    private static void showDialog(List<JarBatchApprovalProtocol.Entry> entries, Path resp) {
        JFrame frame = new JFrame("ZombieBuddy — Java mod approval " + JarBatchApprovalProtocol.osTag());
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(2);
            }
        });

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel intro = new JLabel(
            "<html>For each mod choose <b>Yes</b> (load JAR) or <b>No</b> (block). "
                + "Valid <b>ZBS</b> rows are green; invalid/tampered rows are red (Allow stays <b>No</b>). "
                + "The checkbox below toggles <b>session vs persist</b> for your choices — including denials on bad-signature rows.</html>"
        );
        root.add(intro, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 8, 3, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;

        Font base = UIManager.getFont("Label.font");
        Font bold = base != null ? base.deriveFont(Font.BOLD) : null;

        JLabel hName = new JLabel("Mod name");
        JLabel hAuthor = new JLabel("Author");
        JLabel hUpdated = new JLabel("Updated");
        JLabel hTrust = new JLabel("Trust author");
        JLabel hAllow = new JLabel("Allow");
        if (bold != null) {
            hName.setFont(bold);
            hAuthor.setFont(bold);
            hUpdated.setFont(bold);
            hTrust.setFont(bold);
            hAllow.setFont(bold);
        }
        c.gridx = 0;
        c.weightx = 0.26;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.add(hName, c);
        c.gridx = 1;
        c.weightx = 0.30;
        grid.add(hAuthor, c);
        c.gridx = 2;
        c.weightx = 0.14;
        grid.add(hUpdated, c);
        c.gridx = 3;
        c.weightx = 0.12;
        grid.add(hTrust, c);
        c.gridx = 4;
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;
        grid.add(hAllow, c);

        @SuppressWarnings("unchecked")
        final JRadioButton[] allowYes = new JRadioButton[entries.size()];

        int i = 0;
        for (JarBatchApprovalProtocol.Entry e : entries) {
            boolean zbsYes = "yes".equals(e.zbsValid);
            boolean zbsNo = "no".equals(e.zbsValid);
            Color rowBg = zbsYes ? ZBS_ROW_OK : (zbsNo ? ZBS_ROW_BAD : null);

            c.gridy = i + 1;
            c.gridx = 0;
            c.weightx = 0.26;
            c.fill = GridBagConstraints.HORIZONTAL;
            JLabel nameLab = new JLabel(modTitle(e));
            String tip = "<html>" + escapeHtml(e.jarAbsolutePath)
                + "<br/><b>SHA-256:</b> " + escapeHtml(e.sha256) + "</html>";
            nameLab.setToolTipText(tip);
            if (rowBg != null) {
                nameLab.setOpaque(true);
                nameLab.setBackground(rowBg);
            }
            grid.add(nameLab, c);

            c.gridx = 1;
            c.weightx = 0.30;
            JPanel authorCell = new JPanel();
            authorCell.setLayout(new BoxLayout(authorCell, BoxLayout.PAGE_AXIS));
            authorCell.setOpaque(rowBg != null);
            if (rowBg != null) {
                authorCell.setBackground(rowBg);
            }
            if (zbsYes && e.zbsSteamId != null && !e.zbsSteamId.isEmpty()) {
                String profileUrl = ZBSVerifier.steamCommunityProfileUrl(e.zbsSteamId);
                String linkText = (e.author != null && !e.author.trim().isEmpty()) ? e.author.trim() : e.zbsSteamId;
                JLabel linkLab = new JLabel(
                    "<html><a href=\"" + profileUrl + "\">" + escapeHtml(linkText) + "</a></html>");
                linkLab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                linkLab.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent ev) {
                        openUri(profileUrl);
                    }
                });
                if (rowBg != null) {
                    linkLab.setOpaque(true);
                    linkLab.setBackground(rowBg);
                }
                authorCell.add(linkLab);
            } else if (zbsNo) {
                JLabel warn = new JLabel("<html><font color=\"#b00000\">" + escapeHtml(
                    e.zbsNotice != null && !e.zbsNotice.isEmpty()
                        ? e.zbsNotice
                        : "Invalid signature — JAR may have been tampered with."
                ) + "</font></html>");
                warn.setAlignmentX(Component.LEFT_ALIGNMENT);
                if (rowBg != null) {
                    warn.setOpaque(true);
                    warn.setBackground(rowBg);
                }
                authorCell.add(warn);
            } else {
                String authorText = (e.author != null && !e.author.trim().isEmpty()) ? e.author : "—";
                JLabel plain = new JLabel(authorText);
                if (rowBg != null) {
                    plain.setOpaque(true);
                    plain.setBackground(rowBg);
                }
                authorCell.add(plain);
            }
            grid.add(authorCell, c);

            c.gridx = 2;
            c.weightx = 0.14;
            JLabel dateLab = new JLabel(e.modifiedHuman != null && !e.modifiedHuman.isEmpty() ? e.modifiedHuman : "—");
            if (rowBg != null) {
                dateLab.setOpaque(true);
                dateLab.setBackground(rowBg);
            }
            grid.add(dateLab, c);

            c.gridx = 3;
            c.weightx = 0.12;
            JCheckBox trustCb = new JCheckBox("", false);
            trustCb.setEnabled(zbsYes);
            trustCb.setOpaque(rowBg != null);
            if (rowBg != null) {
                trustCb.setBackground(rowBg);
            }
            grid.add(trustCb, c);

            boolean defaultYes = Loader.DECISION_YES.equals(e.priorHint);
            JRadioButton yesB = new JRadioButton("Yes", defaultYes);
            JRadioButton noB = new JRadioButton("No", !defaultYes);
            if (zbsNo) {
                yesB.setEnabled(false);
                noB.setEnabled(false);
                noB.setSelected(true);
            }
            ButtonGroup grp = new ButtonGroup();
            grp.add(yesB);
            grp.add(noB);
            allowYes[i] = yesB;

            JPanel radios = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            radios.setOpaque(rowBg != null);
            if (rowBg != null) {
                radios.setBackground(rowBg);
            }
            radios.add(yesB);
            radios.add(noB);
            c.gridx = 4;
            c.weightx = 0.0;
            c.fill = GridBagConstraints.NONE;
            grid.add(radios, c);
            i++;
        }

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setPreferredSize(new Dimension(960, 420));
        root.add(scroll, BorderLayout.CENTER);

        JCheckBox savePersist = new JCheckBox(
            "Save decisions to disk (persist across game launches)", true);
        savePersist.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        buttons.add(cancel);
        buttons.add(ok);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.PAGE_AXIS));
        south.add(savePersist);
        south.add(Box.createVerticalStrut(8));
        south.add(buttons);
        root.add(south, BorderLayout.SOUTH);

        cancel.addActionListener(ev -> System.exit(2));
        ok.addActionListener(ev -> {
            try {
                boolean persist = savePersist.isSelected();
                List<JarBatchApprovalProtocol.OutLine> out = new ArrayList<>(entries.size());
                for (int k = 0; k < entries.size(); k++) {
                    JarBatchApprovalProtocol.Entry e = entries.get(k);
                    String tok;
                    if ("no".equals(e.zbsValid)) {
                        // Always deny loading; same session/persist split as other "No" rows.
                        tok = persist
                            ? JarBatchApprovalProtocol.TOK_DENY_PERSIST
                            : JarBatchApprovalProtocol.TOK_DENY_SESSION;
                    } else {
                        boolean allow = allowYes[k].isSelected();
                        if (allow && persist) {
                            tok = JarBatchApprovalProtocol.TOK_ALLOW_PERSIST;
                        } else if (allow) {
                            tok = JarBatchApprovalProtocol.TOK_ALLOW_SESSION;
                        } else if (persist) {
                            tok = JarBatchApprovalProtocol.TOK_DENY_PERSIST;
                        } else {
                            tok = JarBatchApprovalProtocol.TOK_DENY_SESSION;
                        }
                    }
                    out.add(new JarBatchApprovalProtocol.OutLine(e.modKey, e.sha256, tok));
                }
                JarBatchApprovalProtocol.writeResponse(resp, out);
                System.exit(0);
            } catch (Exception ex) {
                ex.printStackTrace(System.err);
                System.exit(2);
            }
        });

        frame.setContentPane(root);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void openUri(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
