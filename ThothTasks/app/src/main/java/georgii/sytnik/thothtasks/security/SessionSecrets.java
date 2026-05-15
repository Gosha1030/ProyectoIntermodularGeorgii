package georgii.sytnik.thothtasks.security;

public final class SessionSecrets {

    private static volatile char[] passwordChars;

    private SessionSecrets() {
    }

    public static char[] getPassword() {
        return passwordChars;
    }

    public static void setPassword(char[] pwd) {
        passwordChars = pwd;
    }

    public static void clear() {
        passwordChars = null;
    }
}