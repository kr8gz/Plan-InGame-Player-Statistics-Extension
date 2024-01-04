package io.github.kr8gz.plan_ingame_player_statistics_extension.database;

/**
 * Exception thrown when a database cannot be initialized.
 * The cause can be inspected by using the {@link #getCause()} method.
 */
public class DatabaseInitializationException extends Exception {
    public DatabaseInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
