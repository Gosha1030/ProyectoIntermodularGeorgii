package georgii.sytnik.thothtasks.domain.travel;

import org.json.JSONObject;

import georgii.sytnik.thothtasks.db.entities.UserEntity;

public final class TravelSettings {

    private TravelSettings() {}

    public static class Params {
        public final int mandatoryExtraM;
        public final int optionalExtraM;
        public Params(int mandatoryExtraM, int optionalExtraM) {
            this.mandatoryExtraM = Math.max(0, mandatoryExtraM);
            this.optionalExtraM = Math.max(0, optionalExtraM);
        }
    }

    public static Params read(UserEntity u) {
        try {
            JSONObject o = (u.ajustesJson == null || u.ajustesJson.trim().isEmpty())
                    ? new JSONObject()
                    : new JSONObject(u.ajustesJson);

            int mandatory = o.optInt("travelExtraMandatoryM", 0);
            int optional = o.optInt("travelExtraOptionalM", 0);
            return new Params(mandatory, optional);
        } catch (Exception e) {
            return new Params(0, 0);
        }
    }

    public static void writeDefaultsIfMissing(UserEntity u) {
        try {
            JSONObject o = (u.ajustesJson == null || u.ajustesJson.trim().isEmpty())
                    ? new JSONObject()
                    : new JSONObject(u.ajustesJson);

            boolean changed = false;
            if (!o.has("travelExtraMandatoryM")) { o.put("travelExtraMandatoryM", 0); changed = true; }
            if (!o.has("travelExtraOptionalM")) { o.put("travelExtraOptionalM", 0); changed = true; }

            if (changed) u.ajustesJson = o.toString();
        } catch (Exception ignored) {}
    }
}