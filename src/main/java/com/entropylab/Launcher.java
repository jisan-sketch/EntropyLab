package com.entropylab;

/**
 * Bootstrap launcher for EntropyLab desktop application.
 * Bypasses JavaFX runtime checking when launched as a standalone JAR or native package.
 */
public class Launcher {
    public static void main(String[] args) {
        EntropyLabApp.main(args);
    }
}
