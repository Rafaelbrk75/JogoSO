package client;

import common.PlayerClass;

import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

public final class ImageAssets {

    private static final Map<PlayerClass, ImageIcon> CACHE = new EnumMap<>(PlayerClass.class);

    private ImageAssets() {
    }

    public static ImageIcon getIcon(PlayerClass clazz) {
        return CACHE.computeIfAbsent(clazz, ImageAssets::loadIcon);
    }

    private static ImageIcon loadIcon(PlayerClass clazz) {
        String resourceName = "/img/" + clazz.name().toLowerCase() + ".png";
        URL url = ImageAssets.class.getResource(resourceName);
        if (url == null) {
            url = resolveFromFileSystem(resourceName);
        }
        if (url != null) {
            return new ImageIcon(url);
        }
        return generatePlaceholder(clazz);
    }

    private static URL resolveFromFileSystem(String resourceName) {
        String normalized = resourceName.startsWith("/") ? resourceName.substring(1) : resourceName;
        String[] bases = {
                "java/build",
                "java/resources",
                "resources",
                "."
        };
        for (String base : bases) {
            File candidate = new File(base, normalized);
            if (candidate.exists() && candidate.isFile()) {
                try {
                    return candidate.toURI().toURL();
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    private static ImageIcon generatePlaceholder(PlayerClass clazz) {
        int width = 260;
        int height = 360;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color base;
        switch (clazz) {
            case GUERREIRO:
                base = new Color(139, 92, 60);
                break;
            case MAGO:
                base = new Color(59, 99, 168);
                break;
            case ARQUEIRO:
                base = new Color(67, 120, 78);
                break;
            default:
                base = new Color(120, 120, 120);
        }

        g.setColor(new Color(250, 240, 215));
        g.fill(new RoundRectangle2D.Double(0, 0, width, height, 32, 32));

        g.setColor(base.darker());
        g.setStroke(new java.awt.BasicStroke(6f));
        g.draw(new RoundRectangle2D.Double(6, 6, width - 12, height - 12, 28, 28));

        g.setColor(base);
        g.fillRoundRect(24, 36, width - 48, height - 120, 24, 24);

        g.setColor(Color.WHITE);
        Font titleFont = g.getFont().deriveFont(Font.BOLD, 32f);
        g.setFont(titleFont);
        drawCentered(g, clazz.toString().toUpperCase(), width, 28);

        Font subtitleFont = g.getFont().deriveFont(Font.BOLD, 22f);
        g.setFont(subtitleFont);
        drawCentered(g, "EM DESENVOLVIMENTO", width, height - 60);

        g.dispose();
        return new ImageIcon(image);
    }

    private static void drawCentered(Graphics2D g, String text, int width, int y) {
        FontMetrics metrics = g.getFontMetrics();
        int x = (width - metrics.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }
}

