package georgii.sytnik.thothtasks.ui.schedule;

import java.util.Calendar;

public interface ScheduleNavigator {
    void navigateToDay(Calendar day);

    void navigateToWeek(Calendar anyDayInWeek);

    void navigateToMonth(Calendar anyDayInMonth);
}