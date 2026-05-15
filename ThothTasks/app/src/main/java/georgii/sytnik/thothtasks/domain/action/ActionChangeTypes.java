package georgii.sytnik.thothtasks.domain.action;

public final class ActionChangeTypes {
    private ActionChangeTypes() {
    }

    public static String on(String key) {
        return key + "_on";
    }

    public static String off(String key) {
        return key + "_off";
    }
}