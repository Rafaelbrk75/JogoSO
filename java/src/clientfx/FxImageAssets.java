package clientfx;

import common.PlayerClass;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

public final class FxImageAssets {

    private static final Map<PlayerClass, Image> CACHE = new EnumMap<>(PlayerClass.class);

    private FxImageAssets() {
    }

    public static Image getImage(PlayerClass clazz) {
        return CACHE.computeIfAbsent(clazz, FxImageAssets::loadImage);
    }

    private static Image loadImage(PlayerClass clazz) {
        String resourceName = "/img/" + clazz.name().toLowerCase() + ".png";
        try (InputStream stream = FxImageAssets.class.getResourceAsStream(resourceName)) {
            if (stream != null) {
                return new Image(stream);
            }
        } catch (IOException ignored) {
        }

        URL url = resolveFromFileSystem(resourceName);
        if (url != null) {
            return new Image(url.toExternalForm());
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

    private static Image generatePlaceholder(PlayerClass clazz) {
        int width = 320;
        int height = 420;
        Canvas canvas = new Canvas(width, height);
        GraphicsContext g = canvas.getGraphicsContext2D();

        Color base;
        switch (clazz) {
            case GUERREIRO:
                base = Color.web("#8b5c3c");
                break;
            case MAGO:
                base = Color.web("#3b63a8");
                break;
            case ARQUEIRO:
                base = Color.web("#43784e");
                break;
            default:
                base = Color.web("#676b74");
        }

        g.setFill(Color.web("#f6f1de"));
        g.fillRoundRect(0, 0, width, height, 36, 36);

        g.setStroke(base.darker());
        g.setLineWidth(6);
        g.strokeRoundRect(6, 6, width - 12, height - 12, 28, 28);

        g.setFill(base);
        g.fillRoundRect(28, 44, width - 56, height - 140, 24, 24);

        g.setFill(Color.WHITE);
        g.setFont(Font.font("Verdana", FontWeight.BOLD, 32));
        drawCentered(g, clazz == PlayerClass.UNKNOWN ? "AGUARDANDO" : clazz.toString(), width, 52);

        g.setFont(Font.font("Verdana", FontWeight.BOLD, 20));
        drawCentered(g, clazz == PlayerClass.UNKNOWN ? "Selecione sua classe" : "Arte indisponível", width, height - 60);

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    private static void drawCentered(GraphicsContext g, String text, int width, int y) {
        javafx.scene.text.Text helper = new javafx.scene.text.Text(text);
        helper.setFont(g.getFont());
        double w = helper.getLayoutBounds().getWidth();
        double x = (width - w) / 2.0;
        g.fillText(text, x, y);
    }
}

