package georgii.sytnik.thothtasks.net;

import android.content.Context;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.PendingOutboxEntity;

public final class ReliableSender {

    private ReliableSender() {}

    /** Send once immediately and enqueue for retry until ACK arrives. */
    public static void sendReliable(Context ctx, String peerIp, int peerPort, String peerKey, byte[] msgIdBytes, String payloadJson) {
        AppDatabase db = AppDatabase.get(ctx);

        long now = System.currentTimeMillis();
        PendingOutboxEntity e = new PendingOutboxEntity();
        e.msgId = msgIdBytes;
        e.peerKey = peerKey;
        e.payloadJson = payloadJson;
        e.attempts = 0;
        e.nextRetryUtcMs = now + 1000; // first retry in 1s
        e.createdUtcMs = now;
        db.outboxDao().upsert(e);

        // send immediately
        sendRaw(peerIp, peerPort, payloadJson);
    }

    public static void sendRaw(String ip, int port, String payloadJson) {
        try {
            byte[] data = payloadJson.getBytes(StandardCharsets.UTF_8);
            DatagramSocket s = new DatagramSocket();
            DatagramPacket p = new DatagramPacket(data, data.length, InetAddress.getByName(ip), port);
            s.send(p);
            s.close();
        } catch (Exception ignored) {}
    }
}