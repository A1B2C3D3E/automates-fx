package automates.ui;

import javafx.application.Application;

/**
 * Point d'entrée. Séparé de MainApp (qui étend Application) pour que le
 * programme puisse aussi être lancé depuis un jar ou le classpath classique.
 */
public final class Launcher {
    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }
}
