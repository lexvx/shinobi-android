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
    /** HorizontalScrollView measures children with UNSPECIFIED width; retain explicit day width. */
    public static int contentWidth(int viewportWidth){return Math.max(1,Math.min(Integer.MAX_VALUE/4,viewportWidth))*4;}
    public static long timeAt(double x, double width, long start, long end) {
        if (width <= 0 || end <= start) return start;
        return clamp(start + (long)(Math.max(0, Math.min(1, x / width)) * (end - start)), start, end);
    }
    public static double position(long time, double width, long start, long end) {
        if (width <= 0 || end <= start) return 0;
        return Math.max(0, Math.min(1, (double)(time - start) / (end - start))) * width;
    }
    public static int centeredScroll(double position, int contentWidth, int viewportWidth) {
        return (int)Math.max(0, Math.min(Math.max(0, contentWidth - viewportWidth), position - viewportWidth / 2.0));
    }
}
