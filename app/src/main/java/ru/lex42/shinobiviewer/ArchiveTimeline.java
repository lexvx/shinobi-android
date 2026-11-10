package ru.lex42.shinobiviewer;

import java.util.List;

/** Pure absolute-time rules shared by the archive wall and its tests. */
public final class ArchiveTimeline {
    public interface Interval { long startMs(); long endMs(); }
    private ArchiveTimeline() {}
    public static <T extends Interval> T at(List<T> rows, long time) {
        T found = null;
        for (T row : rows) if (row.startMs() <= time && time < row.endMs()
                && (found == null || row.startMs() > found.startMs())) found = row;
        return found;
    }
    public static long clamp(long time, long start, long end) {
        return Math.max(start, Math.min(Math.max(start, end - 1), time));
    }
    public static int offset(Interval row, long time) {
        return (int)Math.min(Integer.MAX_VALUE, Math.max(0, time - row.startMs()));
    }
}
