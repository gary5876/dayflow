package dayflow.ui;

import dayflow.model.Schedule;
import dayflow.store.DataFileException;
import dayflow.store.ScheduleBook;
import dayflow.store.ScheduleFile;
import dayflow.store.Settings;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Starts DayFlow: finds the data file, reads it and opens the main window. */
public final class DayFlowApp {
    private DayFlowApp() {
    }

    /**
     * Starts the program.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        Path folder = dataFolder();
        ScheduleFile file = new ScheduleFile(folder);
        boolean existed = !Files.notExists(file.path());
        boolean unreadable = false;
        boolean startupSaveFailed = false;
        List<Schedule> loaded;
        try {
            loaded = file.load();
        } catch (DataFileException e) {
            // R-SAVE-5: the file was renamed to .bak, so starting empty loses nothing.
            loaded = new ArrayList<>();
            unreadable = true;
            startupSaveFailed = !saveEmpty(file);
        } catch (IOException e) {
            // R-SAVE-6: starting empty here could later overwrite a file that is only unreachable for now.
            showCouldNotOpenAndExit();
            return;
        }
        List<Schedule> schedules = loaded;
        boolean showUnreadable = unreadable;
        boolean saveFailed = startupSaveFailed;
        boolean created = !existed && !unreadable;

        SwingUtilities.invokeLater(() -> {
            Clock clock = Clock.systemDefaultZone();
            ScheduleBook book = new ScheduleBook(schedules, file::save, clock);
            ViewState view = new ViewState(clock, Settings.load(folder));
            MainFrame[] frame = new MainFrame[1];
            ScheduleActions actions = new ScheduleActions(book, view, () -> frame[0]);
            frame[0] = new MainFrame(book, view, actions);
            frame[0].startupSaveFailed(saveFailed);
            if (created) {
                frame[0].startupMessage("New data file created.");
            }
            frame[0].setLocationRelativeTo(null);
            frame[0].setVisible(true);
            if (showUnreadable) {
                JOptionPane.showMessageDialog(frame[0],
                        "The data file could not be read. It was renamed to dayflow.dat.bak and DayFlow started empty.",
                        "DayFlow", JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

    /** Dialogs belong on the event thread, so the main thread waits for it. Then the program ends. */
    private static void showCouldNotOpenAndExit() {
        try {
            SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(null,
                    "The data file could not be opened. DayFlow will close.", "DayFlow", JOptionPane.ERROR_MESSAGE));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            // There is nobody left to tell, and the program ends either way.
        }
        System.exit(1);
    }

    /**
     * Gives the empty start a file again and tells whether that worked. If it did not, the window says
     * Not saved until a later save works (R-SAVE-4).
     */
    private static boolean saveEmpty(ScheduleFile file) {
        try {
            file.save(List.of());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** D-29: {@code ~/DayFlow}, or the folder of the system property {@code dayflow.home}. */
    static Path dataFolder() {
        String home = System.getProperty("dayflow.home");
        return home != null ? Path.of(home) : Path.of(System.getProperty("user.home"), "DayFlow");
    }
}
