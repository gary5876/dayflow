package dayflow.plan;

import dayflow.model.Occurrence;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * First, simple placement rule: take the deadlines in order of due time and give each one
 * the earliest free half-hour slots before it is due.
 * A provisional rule; a better one can replace it behind {@link WorkPlanner}.
 */
public final class SimplePlanner implements WorkPlanner {
    private static final int SLOT_MINUTES = 30;
    private static final LocalTime DAY_START = LocalTime.of(9, 0);
    private static final LocalTime DAY_END = LocalTime.of(22, 0);
    private static final LocalTime DINNER_START = LocalTime.of(18, 0);
    private static final LocalTime DINNER_END = LocalTime.of(19, 0);
    private static final int MAX_BLOCK_SLOTS = 4;

    @Override
    public Plan plan(List<Occurrence> schedules, LocalDateTime from, LocalDate until) {
        List<LocalDateTime> free = freeSlots(schedules, from, until);
        List<Occurrence> due = schedules.stream()
                .filter(Occurrence::isOpenDeadline)
                .filter(o -> o.due().isAfter(from) && !o.date().isAfter(until))
                .sorted(Comparator.comparing(Occurrence::due))
                .toList();
        List<Occurrence> noWork = due.stream().filter(o -> o.schedule().effectiveWorkMinutes() == 0).toList();
        List<Occurrence> deadlines = due.stream().filter(o -> remainingMinutes(o, schedules, from, until) > 0).toList();

        List<WorkBlock> blocks = new ArrayList<>();
        Map<Occurrence, Integer> shortMinutes = new LinkedHashMap<>();
        for (Occurrence deadline : deadlines) {
            int needed = remainingMinutes(deadline, schedules, from, until) / SLOT_MINUTES;
            List<LocalDateTime> taken = new ArrayList<>();
            for (LocalDateTime slot : free) {
                if (taken.size() == needed) {
                    break;
                }
                if (!slot.plusMinutes(SLOT_MINUTES).isAfter(deadline.due())) {
                    taken.add(slot);
                }
            }
            free.removeAll(taken);
            blocks.addAll(toBlocks(deadline, taken));
            if (taken.size() < needed) {
                shortMinutes.put(deadline, (needed - taken.size()) * SLOT_MINUTES);
            }
        }
        return new Plan(blocks, shortMinutes, noWork);
    }

    /** Half-hour slots inside the working day that no timed schedule covers. Unstarted work blocks are ignored. */
    private static List<LocalDateTime> freeSlots(List<Occurrence> schedules, LocalDateTime from, LocalDate until) {
        // A work block that has already started is kept, so its time is taken like any other schedule.
        List<Occurrence> busy = schedules.stream()
                .filter(o -> o.timed() && (!o.planned() || o.date().atTime(o.start()).isBefore(from))).toList();
        List<LocalDateTime> free = new ArrayList<>();
        for (LocalDate day = from.toLocalDate(); !day.isAfter(until); day = day.plusDays(1)) {
            for (LocalTime t = DAY_START; t.isBefore(DAY_END); t = t.plusMinutes(SLOT_MINUTES)) {
                LocalDateTime slot = day.atTime(t);
                LocalTime slotEnd = t.plusMinutes(SLOT_MINUTES);
                boolean dinner = t.isBefore(DINNER_END) && slotEnd.isAfter(DINNER_START);
                LocalDate date = day;
                LocalTime time = t;
                boolean taken = busy.stream().anyMatch(o -> o.date().equals(date)
                        && o.start().isBefore(slotEnd) && o.end().isAfter(time));
                if (!slot.isBefore(from) && !dinner && !taken) {
                    free.add(slot);
                }
            }
        }
        return free;
    }

    /** Joins neighbouring slots into blocks of at most two hours. */
    private static List<WorkBlock> toBlocks(Occurrence deadline, List<LocalDateTime> slots) {
        List<WorkBlock> blocks = new ArrayList<>();
        int i = 0;
        while (i < slots.size()) {
            LocalDateTime start = slots.get(i);
            int length = 1;
            while (i + length < slots.size() && length < MAX_BLOCK_SLOTS
                    && slots.get(i + length).equals(start.plusMinutes((long) SLOT_MINUTES * length))) {
                length++;
            }
            blocks.add(new WorkBlock(deadline, start.toLocalDate(), start.toLocalTime(),
                    start.toLocalTime().plusMinutes((long) SLOT_MINUTES * length)));
            i += length;
        }
        return blocks;
    }

    /**
     * Work still to place: the estimate minus the work blocks for this deadline that are kept.
     * Kept are the blocks that have already started and the blocks after the planned period;
     * the unstarted blocks inside the period are replaced by the new plan.
     */
    static int remainingMinutes(Occurrence deadline, List<Occurrence> schedules, LocalDateTime from, LocalDate until) {
        int kept = schedules.stream()
                .filter(o -> o.planned() && o.schedule().plannedFor().is(deadline))
                .filter(o -> o.date().atTime(o.start()).isBefore(from) || o.date().isAfter(until))
                .mapToInt(o -> (o.end().toSecondOfDay() - o.start().toSecondOfDay()) / 60)
                .sum();
        int remaining = deadline.schedule().effectiveWorkMinutes() - kept;
        return Math.max(0, remaining - remaining % SLOT_MINUTES);
    }
}
