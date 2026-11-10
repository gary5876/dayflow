package dayflow.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dayflow.store.Change;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SavedTextTest {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 10, 11, 2);

    @Test
    void beforeAnyChangeItSaysSavedUnlessTheStartSaveFailed() {
        assertTrue(MainFrame.showsSaved(null, false));
        assertFalse(MainFrame.showsSaved(null, true));
    }

    @Test
    void aChangeDecidesByItsOwnSave() {
        assertTrue(MainFrame.showsSaved(new Change("Added: A.", true, AT, false), true));
        assertFalse(MainFrame.showsSaved(new Change("Added: A.", true, null, true), false));
    }
}
