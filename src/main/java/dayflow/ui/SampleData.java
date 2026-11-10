package dayflow.ui;

import dayflow.model.Category;
import dayflow.model.Occurrence;
import dayflow.model.Repeat;
import dayflow.model.Schedule;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Schedules of the target user in October 2026, used to draw the screen designs. */
final class SampleData {
    static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
    private static final LocalDate SEMESTER_END = LocalDate.of(2026, 12, 18);

    private SampleData() {
    }

    static List<Schedule> schedules() {
        List<Schedule> list = new ArrayList<>();
        LocalDate mon = LocalDate.of(2026, 8, 31);
        LocalDate tue = mon.plusDays(1);
        LocalDate wed = mon.plusDays(2);
        LocalDate thu = mon.plusDays(3);
        list.add(weekly("Basic Japanese", mon, "11:00", "13:00", Category.STUDY));
        list.add(weekly("Data Science", mon, "13:00", "15:00", Category.STUDY));
        list.add(weekly("C++ Programming", mon, "15:00", "17:00", Category.STUDY));
        list.add(weekly("Basic Japanese", wed, "11:00", "12:00", Category.STUDY));
        list.add(weekly("Data Science", wed, "13:00", "14:00", Category.STUDY));
        list.add(weekly("C++ Programming", wed, "15:00", "17:00", Category.STUDY));
        for (LocalDate day : List.of(tue, thu)) {
            list.add(weekly("Software System Design", day, "10:30", "12:00", Category.STUDY));
            list.add(weekly("Distributed Systems", day, "12:00", "13:30", Category.STUDY));
            list.add(weekly("Software Engineering", day, "15:00", "16:30", Category.STUDY));
        }
        list.add(weekly("Running crew", tue, "19:30", "21:00", Category.HOBBY));
        list.add(weekly("Tutoring", mon.plusDays(5), "10:00", "12:00", Category.LIFE));
        list.add(Schedule.deadline("Rent payment", LocalDate.of(2026, 9, 25), "23:59", Category.LIFE)
                .repeat(Repeat.MONTHLY, LocalDate.of(2027, 2, 25)).setDone(LocalDate.of(2026, 9, 25), true));

        list.add(job("Hanbit Soft application", 8, "18:00").setDone(d(8), true));
        list.add(job("Dawon Systems application", 9, "23:59").setDone(d(9), true));
        list.add(job("Mirae Data application", 12, "23:59"));
        list.add(job("Seoul Mobility application", 13, "18:00"));
        list.add(job("Nuri Networks application", 13, "23:59").setDone(d(13), true));
        list.add(job("Hana Robotics application", 14, "17:00"));
        list.add(Schedule.deadline("SSD Checkpoint 3 report", d(15), "23:59", Category.STUDY).work(180));
        list.add(job("Blue Cloud application", 16, "15:00"));
        list.add(job("Greenlab AI application", 16, "17:00"));
        list.add(job("Daehan Energy IT application", 16, "18:00"));
        list.add(job("Pixel Games application", 16, "23:59"));
        list.add(job("Orbit Fintech application", 16, "23:59"));
        list.add(Schedule.deadline("TOEIC registration closes", d(19), "18:00", Category.CERTIFICATE));
        list.add(job("Hangang Logistics application", 20, "18:00"));
        list.add(Schedule.deadline("Distributed Systems homework 2", d(21), "23:59", Category.STUDY));
        list.add(job("Maru Security application", 23, "17:00"));
        list.add(job("Saebyeok Bio IT application", 23, "23:59"));
        list.add(Schedule.deadline("Data contest submission", d(30), "18:00", Category.STUDY).work(360));

        list.add(Schedule.timed("Coding test: Nuri Networks", d(17), "14:00", "16:00", Category.JOB));
        list.add(Schedule.allDay("Result: Hanbit Soft documents", d(15), Category.JOB));
        list.add(Schedule.timed("Concert ticket opening", d(20), "20:00", "20:30", Category.HOBBY));
        list.add(Schedule.allDay("Result: Dawon Systems documents", d(22), Category.JOB));
        list.add(Schedule.timed("Han River picnic", d(25), "13:00", "17:00", Category.HOBBY));
        list.add(Schedule.timed("Midterm: Data Science", d(26), "13:00", "15:00", Category.STUDY));
        list.add(Schedule.timed("Midterm: Distributed Systems", d(27), "12:00", "13:30", Category.STUDY));
        list.add(Schedule.timed("Graduation project review", d(29), "13:30", "15:00", Category.STUDY));
        return list;
    }

    static List<Occurrence> occurrences(List<Schedule> schedules, LocalDate from, LocalDate to) {
        return schedules.stream().flatMap(s -> s.occurrences(from, to).stream())
                .sorted(Comparator.comparing(Occurrence::date).thenComparing(Occurrence::sortTime))
                .toList();
    }

    private static Schedule weekly(String title, LocalDate first, String start, String end, Category category) {
        return Schedule.timed(title, first, start, end, category).repeat(Repeat.WEEKLY, SEMESTER_END);
    }

    private static Schedule job(String title, int day, String due) {
        return Schedule.deadline(title, d(day), due, Category.JOB);
    }

    private static LocalDate d(int dayOfMonth) {
        return LocalDate.of(2026, 10, dayOfMonth);
    }
}
