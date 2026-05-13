package georgii.sytnik.thothtasks.security;

/**
 * Stable keys stored in User.ajustesJson.
 * Keep these strings stable forever (migrations depend on them).
 */
public final class ActionSettingsKeys {

    private ActionSettingsKeys() {}

    // Actions planner horizon
    public static final String ACTION_PLAN_DAYS_AHEAD = "actionPlanDaysAhead";

    // Alarm notification style
    public static final String ALARM_ENABLED_SOUND = "alarmEnabledSound";
    public static final String ALARM_VIBRATE = "alarmVibrate";
    public static final String ALARM_SOUND_URI = "alarmSoundUri"; // optional

    // Travels extras
    public static final String TRAVEL_EXTRA_MANDATORY_M = "travelExtraMandatoryM";
    public static final String TRAVEL_EXTRA_OPTIONAL_M = "travelExtraOptionalM";

    // UX
    public static final String ASK_PASSWORD = "askPassword";
}
