package georgii.sytnik.thothtasks.security;

import org.json.JSONObject;

import java.security.SecureRandom;

import georgii.sytnik.thothtasks.db.entities.UserEntity;

public final class UserSettingsCrypto {

    private static final String KEY_SALT_B64 = "netSaltB64";
    private static final String KEY_ITERS = "netKdfIters";

    private UserSettingsCrypto() {
    }

    public static Params ensureParams(UserEntity u) {
        try {
            JSONObject o = (u.ajustesJson == null || u.ajustesJson.trim().isEmpty())
                    ? new JSONObject()
                    : new JSONObject(u.ajustesJson);

            boolean changed = false;

            String saltB64 = o.optString(KEY_SALT_B64, null);
            int iters = o.optInt(KEY_ITERS, 200_000);

            byte[] salt;
            if (saltB64 == null || saltB64.isEmpty()) {
                salt = new byte[16];
                new SecureRandom().nextBytes(salt);
                o.put(KEY_SALT_B64, B64.enc(salt));
                changed = true;
            } else {
                salt = B64.dec(saltB64);
                if (salt.length < 8) {
                    salt = new byte[16];
                    new SecureRandom().nextBytes(salt);
                    o.put(KEY_SALT_B64, B64.enc(salt));
                    changed = true;
                }
            }

            if (!o.has(KEY_ITERS)) {
                o.put(KEY_ITERS, iters);
                changed = true;
            }

            if (changed) {
                u.ajustesJson = o.toString();
            }

            return new Params(salt, iters, changed);

        } catch (Exception e) {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            u.ajustesJson = null;
            return new Params(salt, 200_000, false);
        }
    }

    public record Params(byte[] salt, int iters, boolean changed) {
    }
}