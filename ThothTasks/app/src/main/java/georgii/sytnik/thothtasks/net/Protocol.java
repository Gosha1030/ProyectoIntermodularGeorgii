package georgii.sytnik.thothtasks.net;

public final class Protocol {
    public static final int VER = 1;
    public static final String HELLO = "HELLO";
    public static final String ACK = "ACK";
    public static final String SESSION_HELLO = "SESSION_HELLO";
    public static final String SESSION_CHALLENGE = "SESSION_CHALLENGE";
    public static final String SESSION_RESULT = "SESSION_RESULT";
    public static final String ACCESS_REQUEST = "ACCESS_REQUEST";
    public static final String ACCESS_RESULT = "ACCESS_RESULT";
    public static final String SCHEDULE_SUMMARY_REQUEST = "SCHEDULE_SUMMARY_REQUEST";
    public static final String SCHEDULE_SUMMARY_RESPONSE = "SCHEDULE_SUMMARY_RESPONSE";
    public static final String SYNC_REQUEST = "SYNC_REQUEST";
    public static final String SYNC_CHUNK = "SYNC_CHUNK";
    public static final String SYNC_DONE = "SYNC_DONE";
    public static final String ERROR = "ERROR";
    public static final String ACTION_ACCEPT = "georgii.sytnik.thothtasks.ACTION_ACCEPT";
    public static final String ACTION_REJECT = "georgii.sytnik.thothtasks.ACTION_REJECT";
    public static final String ACTION_BLOCK = "georgii.sytnik.thothtasks.ACTION_BLOCK";
    public static final String EXTRA_PEER_IP = "peer_ip";
    public static final String EXTRA_PEER_PORT = "peer_port";
    public static final String EXTRA_RESOURCE_ID_HEX = "resource_id_hex";
    public static final String EXTRA_REQUEST_MSGID_HEX = "request_msgid_hex";
    public static final String EXTRA_EXTERNAL_NAME = "external_name";
    public static final String VERSION_REQUEST = "VERSION_REQUEST";
    public static final String VERSION_RESPONSE = "VERSION_RESPONSE";
    private Protocol() {
    }
}