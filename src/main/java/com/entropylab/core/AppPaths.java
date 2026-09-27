package com.entropylab.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves and initializes application filesystem paths on Windows.
 * Paths are resolved dynamically via the %APPDATA% environment variable.
 */
public final class AppPaths {

    private static final String APP_FOLDER_NAME = "EntropyLab";
    private static final String MOCKS_FOLDER_NAME = "mocks";
    private static final String DB_FILE_NAME = "entropylab.db";

    private AppPaths() {
        // Utility class; prevent instantiation
    }

    /**
     * Resolves the application data directory: %APPDATA%\EntropyLab\
     *
     * @return Path representing the app data directory.
     */
    public static Path getAppDataDir() {
        String customHome = System.getProperty("entropylab.home");
        if (customHome != null && !customHome.isBlank()) {
            return Path.of(customHome);
        }
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            String userHome = System.getProperty("user.home", ".");
            return Path.of(userHome, "AppData", "Roaming", APP_FOLDER_NAME);
        }
        return Path.of(appData, APP_FOLDER_NAME);
    }

    /**
     * Resolves the mocks subfolder directory: %APPDATA%\EntropyLab\mocks\
     *
     * @return Path representing the mocks directory.
     */
    public static Path getMocksDir() {
        return getAppDataDir().resolve(MOCKS_FOLDER_NAME);
    }

    /**
     * Resolves the string path to the SQLite database file: %APPDATA%\EntropyLab\entropylab.db
     * Does not create the database file itself.
     *
     * @return Absolute string path to entropylab.db.
     */
    public static String getDbFilePath() {
        return getAppDataDir().resolve(DB_FILE_NAME).toAbsolutePath().toString();
    }

    /**
     * Ensures both the application data directory and the mocks subfolder exist on disk,
     * creating them if they are missing. Logs the result to the console.
     */
    public static void ensureDirectoriesExist() {
        Path appDataDir = getAppDataDir();
        Path mocksDir = getMocksDir();

        try {
            boolean appDataExisted = Files.exists(appDataDir);
            Files.createDirectories(appDataDir);
            if (!appDataExisted) {
                System.out.println("[AppPaths] Created app data directory: " + appDataDir);
            } else {
                System.out.println("[AppPaths] App data directory exists: " + appDataDir);
            }

            boolean mocksExisted = Files.exists(mocksDir);
            Files.createDirectories(mocksDir);
            if (!mocksExisted) {
                System.out.println("[AppPaths] Created mocks directory: " + mocksDir);
            } else {
                System.out.println("[AppPaths] Mocks directory exists: " + mocksDir);
            }
        } catch (IOException e) {
            System.err.println("[AppPaths] Failed to create application directories: " + e.getMessage());
            throw new UncheckedIOException("Failed to create application directories", e);
        }
    }

    /**
     * Bootstrap alias for ensureDirectoriesExist.
     */
    public static void initialize() {
        ensureDirectoriesExist();
    }
}
