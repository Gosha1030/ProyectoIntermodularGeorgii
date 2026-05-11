package georgii.sytnik.thothtasks.net;

import java.util.concurrent.ConcurrentHashMap;

public final class NetSessionStore {

    private static final ConcurrentHashMap<String, NetSession> SESSIONS = new ConcurrentHashMap<>();

    private NetSessionStore() {}

    public static NetSession get(String peerKey) {
        return SESSIONS.get(peerKey);
    }

    public static void put(String peerKey, NetSession s) {
        SESSIONS.put(peerKey, s);
    }

    public static void remove(String peerKey) {
        SESSIONS.remove(peerKey);
    }
}
