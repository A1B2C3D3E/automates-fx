package automates.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Barre « Étape 1 : définir l'alphabet Σ », placée au-dessus des onglets. */
final class AlphabetBar extends VBox {

    private final AlphabetModel model;
    private final TextField field = new TextField();
    private final Label status = new Label();

    AlphabetBar(AlphabetModel model) {
        super(6);
        this.model = model;
        getStyleClass().add("alphabet-bar");
        setPadding(new Insets(10, 14, 10, 14));

        Label title = new Label("Étape 1 — Définir l’alphabet Σ");
        title.getStyleClass().add("step-title");
        Label sigma = new Label("Σ =");
        sigma.setStyle("-fx-font-size:16px; -fx-font-weight:bold;");
        field.getStyleClass().add("mono");
        field.setPromptText("lettres A–Z, a–z ou chiffres 0–9, séparés par des virgules, ex. : a,b");
        field.setPrefColumnCount(30);
        Button ok = new Button("Valider Σ");
        ok.getStyleClass().add("primary");
        ok.setOnAction(e -> apply());
        field.setOnAction(e -> apply());

        FlowPane presets = new FlowPane(6, 6);
        presets.setAlignment(Pos.CENTER_LEFT);
        presets.getChildren().add(UiUtil.muted("Raccourcis :"));
        for (String p : new String[]{"a,b", "0,1", "a,b,c", "a", "a,b,c,d"}) {
            Button b = new Button("{" + p + "}");
            b.getStyleClass().add("chip");
            b.setOnAction(e -> { field.setText(p); apply(); });
            presets.getChildren().add(b);
        }

        HBox row = new HBox(8, sigma, field, ok, presets);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(presets, Priority.NEVER);
        status.setWrapText(true);
        getChildren().addAll(title, row, status);
        showUndefined();
    }

    private void apply() {
        try {
            model.set(field.getText());
            field.setText(String.join(",", model.symbols().stream().map(String::valueOf).toList()));
            status.getStyleClass().removeAll("ko", "ok", "muted");
            status.getStyleClass().add("ok");
            status.setText("✔ " + model.display() + " — seuls ces symboles sont acceptés dans les regex, les transitions et les mots testés.");
        } catch (IllegalArgumentException ex) {
            status.getStyleClass().removeAll("ko", "ok", "muted");
            status.getStyleClass().add("ko");
            status.setText("✘ " + ex.getMessage());
        }
    }

    void showUndefined() {
        status.getStyleClass().removeAll("ko", "ok", "muted");
        status.getStyleClass().add("muted");
        status.setText("⚠ L’alphabet n’est pas encore défini : choisissez-le d’abord. Les onglets sont verrouillés jusque-là.");
    }
}
