package georgii.sytnik.thothtasks.net;

public class NetSession {
    public String sessionId;
    public byte[] kAuth;        // 32 bytes
    public byte[] kAead;        // 32 bytes
    public byte[] noncePrefix4; // 4 bytes
    public long lastSeqIn;
    public long nextSeqOut;
}