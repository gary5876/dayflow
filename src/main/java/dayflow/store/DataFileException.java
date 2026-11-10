package dayflow.store;

import java.nio.file.Path;

/** The data file could not be read. It was renamed to a backup so that the app can start empty (R-SAVE-5). */
public final class DataFileException extends Exception {
    private final transient Path backup;

    /**
     * Creates the exception.
     *
     * @param backup where the unreadable file was moved to
     * @param cause why it could not be read
     */
    public DataFileException(Path backup, Throwable cause) {
        super("Could not read the data file. It was kept as " + backup, cause);
        this.backup = backup;
    }

    /**
     * Where the unreadable file was moved to.
     *
     * @return the path of the backup file
     */
    public Path backup() {
        return backup;
    }
}
