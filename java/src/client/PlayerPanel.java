package client;

import common.PlayerClass;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

public class PlayerPanel extends JPanel {

    private final String title;
    private final JLabel nameLabel = new JLabel("Jogador");
    private final JLabel imageLabel = new JLabel();
    private final JProgressBar hpBar = new JProgressBar(0, 100);
    private final JProgressBar mpBar = new JProgressBar(0, 50);
    private boolean isYou = false;

    public PlayerPanel(String title) {
        this.title = title;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(140, 140, 140), 2, true),
                title));

        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 16f));
        nameLabel.setAlignmentX(CENTER_ALIGNMENT);

        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setAlignmentX(CENTER_ALIGNMENT);
        imageLabel.setPreferredSize(new Dimension(320, 420));

        hpBar.setStringPainted(true);
        hpBar.setForeground(new Color(200, 60, 60));
        mpBar.setStringPainted(true);
        mpBar.setForeground(new Color(80, 120, 200));

        add(nameLabel);
        add(imageLabel);
        add(createLabeledBar("HP", hpBar));
        add(createLabeledBar("MP", mpBar));
        setBackground(new Color(244, 240, 232));
    }

    private JPanel createLabeledBar(String label, JProgressBar bar) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(label);
        title.setAlignmentX(CENTER_ALIGNMENT);
        bar.setAlignmentX(CENTER_ALIGNMENT);
        panel.add(title);
        panel.add(bar);
        panel.setOpaque(false);
        return panel;
    }

    public void updateClass(PlayerClass playerClass, boolean ignoredHasHeal) {
        ImageIcon icon = ImageAssets.getIcon(playerClass);
        applyIcon(icon);
    }

    public void updateStats(int hp, int mp, int cdSkill, int cdHeal) {
        hpBar.setValue(Math.max(0, hp));
        hpBar.setString(hp + " / 100");
        mpBar.setValue(Math.max(0, mp));
        mpBar.setString(mp + " / 50");
        if (hp <= 0) {
            setOut();
        } else {
            resetBackground();
        }
    }

    public void setPlayerName(String name) {
        nameLabel.setText(name);
    }

    public void setIsYou(boolean you) {
        this.isYou = you;
        refreshBorder(false);
    }

    public void setTurnActive(boolean active) {
        refreshBorder(active);
    }

    private void refreshBorder(boolean active) {
        Color borderColor;
        if (!isYou) {
            borderColor = new Color(140, 140, 140);
        } else if (active) {
            borderColor = new Color(60, 140, 220);
        } else {
            borderColor = new Color(0, 102, 170);
        }
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(borderColor, 3, true),
                title));
        revalidate();
        repaint();
    }

    public void reset() {
        hpBar.setValue(100);
        hpBar.setString("100 / 100");
        mpBar.setValue(50);
        mpBar.setString("50 / 50");
        applyIcon(ImageAssets.getIcon(PlayerClass.UNKNOWN));
        resetBackground();
        refreshBorder(false);
    }

    private void resetBackground() {
        setBackground(new Color(244, 240, 232));
    }

    private void setOut() {
        setBackground(new Color(230, 220, 220));
    }

    private void applyIcon(ImageIcon icon) {
        if (icon == null) {
            imageLabel.setIcon(null);
            return;
        }
        int targetWidth = imageLabel.getPreferredSize().width;
        int targetHeight = imageLabel.getPreferredSize().height;
        int width = icon.getIconWidth();
        int height = icon.getIconHeight();
        double scale = Math.min((double) targetWidth / width, (double) targetHeight / height);
        if (scale > 1.0) {
            scale = 1.0;
        }
        int scaledW = Math.max(1, (int) (width * scale));
        int scaledH = Math.max(1, (int) (height * scale));
        java.awt.Image scaled = icon.getImage().getScaledInstance(scaledW, scaledH, java.awt.Image.SCALE_SMOOTH);
        imageLabel.setIcon(new ImageIcon(scaled));
    }
}

