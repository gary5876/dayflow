package dayflow.store;

import java.time.LocalDateTime;

/**
 * The result of one change. {@code savedAt} is empty (null) when the save failed.
 *
 * @param statusText the status bar text, ending with a period and with the count of things removed along
 * @param canUndo whether the Undo link is shown
 * @param savedAt when the data was written
 * @param saveFailed true when the data stayed in memory only (R-SAVE-4)
 */
public record Change(String statusText, boolean canUndo, LocalDateTime savedAt, boolean saveFailed) {
}
