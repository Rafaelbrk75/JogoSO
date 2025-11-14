package clientfx;

import common.PlayerClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

public class FxPlayerCard extends VBox {

    private final Label nameLabel = new Label("Jogador");
    private final ImageView imageView = new ImageView();
    private final ProgressBar hpBar = new ProgressBar(1.0);
    private final Label hpLabel = new Label("HP 100 / 100");
    private final ProgressBar mpBar = new ProgressBar(1.0);
    private final Label mpLabel = new Label("MP 50 / 50");

    public FxPlayerCard(String title) {
        getStyleClass().add("player-card");
        setSpacing(18);
        setAlignment(Pos.TOP_CENTER);

        nameLabel.getStyleClass().add("player-name");
        nameLabel.setText(title);

        imageView.setPreserveRatio(true);
        imageView.setFitWidth(260);
        imageView.setFitHeight(360);
        imageView.getStyleClass().add("player-image");

        hpBar.getStyleClass().add("hp-bar");
        mpBar.getStyleClass().add("mp-bar");
        hpBar.setMaxWidth(Double.MAX_VALUE);
        mpBar.setMaxWidth(Double.MAX_VALUE);

        VBox hpBox = new VBox(4, hpLabel, hpBar);
        hpBox.getStyleClass().add("stat-box");
        hpBox.setAlignment(Pos.CENTER);

        VBox mpBox = new VBox(4, mpLabel, mpBar);
        mpBox.getStyleClass().add("stat-box");
        mpBox.setAlignment(Pos.CENTER);

        getChildren().addAll(nameLabel, imageView, hpBox, mpBox);
        updateClass(PlayerClass.UNKNOWN);
        updateStats(100, 50);
    }

    public void updateClass(PlayerClass playerClass) {
        Image image = FxImageAssets.getImage(playerClass);
        imageView.setImage(image);
    }

    public void updateStats(int hp, int mp) {
        hp = Math.max(0, Math.min(100, hp));
        mp = Math.max(0, Math.min(50, mp));
        hpBar.setProgress(hp / 100.0);
        mpBar.setProgress(mp / 50.0);
        hpLabel.setText("HP " + hp + " / 100");
        mpLabel.setText("MP " + mp + " / 50");
        if (hp <= 0) {
            getStyleClass().add("player-card-out");
        } else {
            getStyleClass().remove("player-card-out");
        }
    }

    public void setTurnActive(boolean active) {
        if (active) {
            if (!getStyleClass().contains("player-card-active")) {
                getStyleClass().add("player-card-active");
            }
        } else {
            getStyleClass().remove("player-card-active");
        }
    }

    public void setPlayerName(String name) {
        nameLabel.setText(name);
    }
}

