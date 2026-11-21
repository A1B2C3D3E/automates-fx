package automates.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        AlphabetModel alpha = new AlphabetModel();
        AlphabetBar bar = new AlphabetBar(alpha);

        RegexTab regexTab = new RegexTab(alpha);
        EditorTab editorTab = new EditorTab(alpha);
        OperationsTab opsTab = new OperationsTab(alpha, editorTab::currentAutomaton);
        TabPane tabs = new TabPane(regexTab, editorTab, opsTab);

        // Tant que Σ n'est pas défini, on ne peut ni saisir de regex ni dessiner d'automate.
        regexTab.getContent().setDisable(true);
        editorTab.getContent().setDisable(true);
        opsTab.getContent().setDisable(true);
        alpha.addListener(() -> {
            regexTab.getContent().setDisable(false);
            editorTab.getContent().setDisable(false);
            opsTab.getContent().setDisable(false);
            regexTab.onAlphabetChanged();
            editorTab.onAlphabetChanged();
            opsTab.onAlphabetChanged();
        });

        // Regex → automates : « Ouvrir dans l'éditeur »
        regexTab.setOnOpenInEditor(m -> {
            editorTab.load(m, null);
            tabs.getSelectionModel().select(editorTab);
        });
        // Éditeur → regex : « Vérifier dans l'onglet 1 »
        editorTab.setOnVerifyRegex(re -> {
            regexTab.setRegexAndBuild(re);
            tabs.getSelectionModel().select(regexTab);
        });

        // Intersection / union : « Ouvrir dans l'éditeur » et « Vérifier la regex »
        opsTab.setOnOpenInEditor(a -> {
            editorTab.load(a, null);
            tabs.getSelectionModel().select(editorTab);
        });
        opsTab.setOnVerifyRegex(re -> {
            regexTab.setRegexAndBuild(re);
            tabs.getSelectionModel().select(regexTab);
        });

        BorderPane root = new BorderPane();
        root.setTop(bar);
        root.setCenter(tabs);
        Scene scene = new Scene(root, 1150, 900);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setTitle("Reconnaisseur de langage régulier — Regex ⇄ AFN ⇄ AFD ⇄ AFD minimal ⇄ ∩ / ∪");
        stage.setScene(scene);
        stage.show();
    }
}
