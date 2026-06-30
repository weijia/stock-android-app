package com.stock.app;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MainActivityTest {

    private Calendar createCalendar(int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    @Test
    public void checkedSwitchKeepsScreenOnOutsideTheWindow() {
        assertTrue(MainActivity.shouldKeepScreenOn(true, createCalendar(20, 0)));
    }

    @Test
    public void uncheckedSwitchUsesTheSpecifiedTimeWindow() {
        assertFalse(MainActivity.shouldKeepScreenOn(false, createCalendar(8, 29)));
        assertTrue(MainActivity.shouldKeepScreenOn(false, createCalendar(8, 30)));
        assertTrue(MainActivity.shouldKeepScreenOn(false, createCalendar(15, 10)));
        assertFalse(MainActivity.shouldKeepScreenOn(false, createCalendar(15, 11)));
    }
}
