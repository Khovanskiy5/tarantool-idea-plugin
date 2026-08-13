package com.khovanskiy.tarantool.jdbcshim;

import java.util.Objects;
import java.util.StringJoiner;

/**
 * Значение Tarantool-типа interval (MsgPack-расширение 6).
 *
 * Поля соответствуют полям интервала Tarantool; adjust — правило переноса
 * конца месяца при арифметике дат: 0 — excess, 1 — none, 2 — last.
 */
public final class TarantoolInterval {

    private final long year;
    private final long month;
    private final long week;
    private final long day;
    private final long hour;
    private final long min;
    private final long sec;
    private final long nsec;
    private final int adjust;

    public TarantoolInterval(long year, long month, long week, long day,
                             long hour, long min, long sec, long nsec, int adjust) {
        this.year = year;
        this.month = month;
        this.week = week;
        this.day = day;
        this.hour = hour;
        this.min = min;
        this.sec = sec;
        this.nsec = nsec;
        this.adjust = adjust;
    }

    public long getYear() {
        return year;
    }

    public long getMonth() {
        return month;
    }

    public long getWeek() {
        return week;
    }

    public long getDay() {
        return day;
    }

    public long getHour() {
        return hour;
    }

    public long getMin() {
        return min;
    }

    public long getSec() {
        return sec;
    }

    public long getNsec() {
        return nsec;
    }

    public int getAdjust() {
        return adjust;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TarantoolInterval)) {
            return false;
        }
        TarantoolInterval that = (TarantoolInterval) other;
        return year == that.year && month == that.month && week == that.week && day == that.day
            && hour == that.hour && min == that.min && sec == that.sec && nsec == that.nsec
            && adjust == that.adjust;
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, month, week, day, hour, min, sec, nsec, adjust);
    }

    @Override
    public String toString() {
        StringJoiner parts = new StringJoiner(", ");
        appendPart(parts, year, "years");
        appendPart(parts, month, "months");
        appendPart(parts, week, "weeks");
        appendPart(parts, day, "days");
        appendPart(parts, hour, "hours");
        appendPart(parts, min, "minutes");
        appendPart(parts, sec, "seconds");
        appendPart(parts, nsec, "nanoseconds");
        return parts.length() > 0 ? parts.toString() : "0 seconds";
    }

    private static void appendPart(StringJoiner parts, long value, String unit) {
        if (value != 0) {
            parts.add(value + " " + unit);
        }
    }
}
