package georgii.sytnik.thothtasks.net;

import android.content.Context;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.PendingOutboxEntity;

public class RetryScheduler implements Runnable {

    private final Context ctx;
    private final AppDatabase db;
    private volatile boolean running = true;

    public RetryScheduler(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.db = AppDatabase.get(this.ctx);
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            try {
                long now = System.currentTimeMillis();
                List<PendingOutboxEntity> due = db.outboxDao().due(now, 10);
                for (PendingOutboxEntity e : due) {
                    sendOnce(e);
                }
                Thread.sleep(400);
            } catch (Exception ignored) {
            }
        }
    }

    private void sendOnce(PendingOutboxEntity e) {
        try {
            String[] parts = e.peerKey.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            byte[] data = e.payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            DatagramSocket socket = new DatagramSocket();
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(ip), port);
            socket.send(packet);
            socket.close();

            int attempts = e.attempts + 1;
            if (attempts >= 6) {
                db.outboxDao().delete(e.msgId);
                return;
            }

            long backoffMs = (1L << attempts) * 1000L;
            e.attempts = attempts;
            e.nextRetryUtcMs = System.currentTimeMillis() + backoffMs;
            db.outboxDao().upsert(e);

        } catch (Exception ex) {
        }
    }
}
