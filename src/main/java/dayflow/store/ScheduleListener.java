package dayflow.store;

/** Registered by the screens to hear about every change of the data, including a failed save. */
public interface ScheduleListener {
    /**
     * Called after every change.
     *
     * @param change what happened
     */
    void scheduleChanged(Change change);
}
