package com.robot.arkadas;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reminders the web app plans ahead (e.g. "Matematik sınavına 3 gün kaldı"). They are kept in prefs
 * and set as alarms, so they show up as normal notifications even when the app is closed.
 */
public class Notices extends BroadcastReceiver {
    private static final String CHANNEL = "hatirlatma";
    private static final int MAX = 40;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("robot", Context.MODE_PRIVATE);
    }

    /** Replaces all planned reminders. json: [{"at": millis, "title": "...", "text": "...", "open": "exams"}] */
    static void schedule(Context c, String json) {
        prefs(c).edit().putString("notices", json).apply();
        reschedule(c);
    }

    /** Sets alarms for the stored reminders; also used after a restart, which clears all alarms. */
    static void reschedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        for (int i = 0; i < MAX; i++) {
            PendingIntent old = PendingIntent.getBroadcast(c, 100 + i, new Intent(c, Notices.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
            if (old != null) { am.cancel(old); old.cancel(); }
        }
        try {
            JSONArray list = new JSONArray(prefs(c).getString("notices", "[]"));
            long now = System.currentTimeMillis();
            for (int i = 0, slot = 0; i < list.length() && slot < MAX; i++) {
                JSONObject n = list.getJSONObject(i);
                long at = n.optLong("at", 0);
                if (at <= now) continue;
                Intent it = new Intent(c, Notices.class)
                        .putExtra("title", n.optString("title"))
                        .putExtra("text", n.optString("text"))
                        .putExtra("open", n.optString("open"));
                PendingIntent pi = PendingIntent.getBroadcast(c, 100 + slot, it,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                slot++;
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Sınav ve hatırlatmalar", NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(c, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN, intent.getStringExtra("open"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 3, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = intent.getStringExtra("text");
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(intent.getStringExtra("title"))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try { nm.notify((int) (System.currentTimeMillis() % 100000) + 10, n); } catch (SecurityException ignored) { }
    }
}
