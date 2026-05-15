package georgii.sytnik.thothtasks.net;

public class NetSession {
    public String sessionId;
    public byte[] kAuth;
    public byte[] kAead;
    public byte[] noncePrefix4;
    public long lastSeqIn;
    public long nextSeqOut;
}