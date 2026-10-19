package com.robot.arkadas;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Random;

/**
 * A small robot that lives over other apps. Most of the time it sleeps; now and then it wakes up,
 * waves, walks a little and says something in a speech bubble, then dozes off again.
 *
 * Gestures: tap a sleeping robot to wake it; tap an awake robot to shrink it (for videos) or grow it back;
 * double tap to open the app and talk; long press to put it to sleep. It only goes away from the
 * notification's "Robotu kapat" action or the app's settings, never by accident.
 */
public class RobotOverlayService extends Service {

    private static final String CHANNEL = "robot_overlay";
    private static final String ACTION_STOP = "stop";
    private static final long FRAME_MS = 33;
    private static final long AWAKE_MS = 25000;
    private static RobotOverlayService instance;
    private static boolean hiddenRequested = false;

    private static final String[] IDLE_LINES = {
            "Merhaba %s! 👋", "Beni özledin mi? 💖", "Hadi biraz oyun oynayalım! 🎲", "Su içmeyi unutma 💧",
            "Bugün ödevini yaptın mı? 📚", "Çok güzel uyudum! 😊", "Bir bilmece sorayım mı? 🧩",
            "Seni seviyorum %s! 💗", "Yıldız toplamaya ne dersin? ⭐", "Gözlerini biraz dinlendir 👀",
            "Dans etmek ister misin? 💃", "Bana yeni bir kıyafet alır mısın? 👗", "Bugün nasılsın %s?",
            "Bana iki kere dokunursan konuşuruz! 💬", "Hihi, ne yapıyorsun? 🤭"
    };
    private static final String[] WAKE_LINES = {
            "Hihi! Gıdıklandım! 😆", "Uyandım! 🌞", "Günaydııın! ☀️", "Merhaba %s! 👋", "Oyun mu oynuyoruz? 🎈"
    };
    private static final String[] SLEEP_LINES = { "İyi uykular… 😴", "Biraz kestireyim… 💤", "Çok uykum geldi… 🥱" };

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private WebView view;
    private TextView bubble;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private SharedPreferences prefs;
    private int size, miniSize;
    private float x, y, tx, ty, speed;
    private boolean moving = false, mini = false, autoMini = false, awake = true, ticking = false;
    private int facing = 1;
    private long pausedUntil = 0, lastTapAt = 0;

    public static boolean isRunning() {
        return instance != null;
    }

    /** Hides the robot while the main screen is open, so there aren't two robots. */
    public static void setHidden(boolean hidden) {
        hiddenRequested = hidden;
        if (instance != null && instance.view != null) {
            instance.view.setVisibility(hidden ? View.GONE : View.VISIBLE);
            if (hidden) instance.hideBubble();
            else instance.onShown();
        }
    }

    /** Loads the newest bubble.html after an update arrived from GitHub. */
    public static void reloadPage() {
        final RobotOverlayService s = instance;
        if (s == null) return;
        s.handler.post(new Runnable() {
            @Override public void run() { s.view.loadUrl(WebAssets.url(s, "bubble.html")); }
        });
    }

    /** Re-applies the robot's colours and clothes after the child dressed it up. */
    public static void refreshLook() {
        if (instance != null) instance.applyLook();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = getSharedPreferences("robot", MODE_PRIVATE);
        goForeground();

        DisplayMetrics dm = getResources().getDisplayMetrics();
        size = (int) (100 * dm.density);
        miniSize = (int) (46 * dm.density);
        speed = 1.4f * dm.density;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        view = new WebView(this);
        view.setBackgroundColor(Color.TRANSPARENT);
        view.getSettings().setJavaScriptEnabled(true);
        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);
        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                applyLook();
                js("setSleep(" + !awake + ")");
            }
        });
        view.getSettings().setAllowFileAccess(true);
        view.loadUrl(WebAssets.url(this, "bubble.html"));
        view.setOnTouchListener(new TouchHandler());
        view.setVisibility(hiddenRequested ? View.GONE : View.VISIBLE);

        lp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        x = prefs.getFloat("ovX", dm.widthPixels - size - 16 * dm.density);
        y = prefs.getFloat("ovY", dm.heightPixels * 0.6f);
        clampToScreen();
        tx = x;
        ty = y;
        lp.x = (int) x;
        lp.y = (int) y;
        wm.addView(view, lp);
        watchFullscreen();
        // Say hello, then nap until something happens.
        handler.postDelayed(goToSleep, AWAKE_MS / 2);
        startTicking();
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Ekrandaki robot", NotificationManager.IMPORTANCE_MIN));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_LISTEN, true),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, RobotOverlayService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(prefs.getString("botName", "Pamuk") + " ekranda")
                .setContentText("Dokun: uyandır · Çift dokun: konuş · Uzun bas: uyut")
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Robotu kapat", stop).build())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            prefs.edit().putBoolean("overlay", false).apply();
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Swiping the app away from recents must not take the robot with it.
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        hideBubble();
        if (view != null) {
            try { wm.removeView(view); } catch (Exception ignored) { }
            view.destroy();
        }
        prefs.edit().putFloat("ovX", x).putFloat("ovY", y).apply();
        instance = null;
        super.onDestroy();
    }

    private void js(String code) {
        if (view != null) view.evaluateJavascript(code, null);
    }

    private void applyLook() {
        js("applyLook(" + org.json.JSONObject.quote(prefs.getString("look", "{}")) + ")");
    }

    private String kidName() {
        String n = prefs.getString("kidName", "").trim();
        return n.isEmpty() ? "tatlım" : n;
    }

    private static boolean isNight() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 21 || h < 7;
    }

    private void clampToScreen() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int s = mini ? miniSize : size;
        x = Math.max(0, Math.min(dm.widthPixels - s, x));
        y = Math.max(0, Math.min(dm.heightPixels - s, y));
    }

    private void updatePosition() {
        lp.x = (int) x;
        lp.y = (int) y;
        try { wm.updateViewLayout(view, lp); } catch (Exception ignored) { }
        positionBubble();
    }

    // ---------- moods ----------

    private final Runnable goToSleep = new Runnable() {
        @Override
        public void run() { sleep(false); }
    };

    private final Runnable spontaneous = new Runnable() {
        @Override
        public void run() {
            if (!isRunning()) return;
            if (view.getVisibility() != View.VISIBLE || mini || isNight()) {
                scheduleSpontaneous();
                return;
            }
            wake(null);
            int pick = rnd.nextInt(4);
            if (pick == 0) js("dance()");
            else if (pick == 1) js("wave()");
            else if (pick == 2) js("stretch()");
            say(String.format(IDLE_LINES[rnd.nextInt(IDLE_LINES.length)], kidName()));
        }
    };

    private void scheduleSpontaneous() {
        handler.removeCallbacks(spontaneous);
        // Every 2-5 minutes; much rarer at night.
        long delay = (120 + rnd.nextInt(180)) * 1000L * (isNight() ? 6 : 1);
        handler.postDelayed(spontaneous, delay);
    }

    private void sleep(boolean announce) {
        awake = false;
        handler.removeCallbacks(goToSleep);
        setMoving(false, facing);
        js("setSleep(true)");
        if (announce) say(SLEEP_LINES[rnd.nextInt(SLEEP_LINES.length)]);
        prefs.edit().putFloat("ovX", x).putFloat("ovY", y).apply();
        scheduleSpontaneous();
    }

    private void wake(String line) {
        awake = true;
        js("setSleep(false)");
        if (line != null) say(line);
        pausedUntil = System.currentTimeMillis() + 2500;
        pickTarget();
        startTicking();
        handler.removeCallbacks(goToSleep);
        handler.postDelayed(goToSleep, AWAKE_MS);
    }

    private void onShown() {
        if (!awake && !mini) wake(String.format(WAKE_LINES[rnd.nextInt(WAKE_LINES.length)], kidName()));
    }

    // ---------- speech bubble (a separate window that never catches touches) ----------

    private final Runnable hideBubbleLater = new Runnable() {
        @Override
        public void run() { hideBubble(); }
    };

    private void say(String text) {
        if (view.getVisibility() != View.VISIBLE || mini) return;
        hideBubble();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextColor(Color.parseColor("#1b2433"));
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        bubble.setMaxWidth((int) (220 * dm.density));
        int pad = (int) (12 * dm.density);
        bubble.setPadding(pad, pad * 2 / 3, pad, pad * 2 / 3);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(18 * dm.density);
        bg.setStroke((int) (2 * dm.density), Color.parseColor("#3ddba4"));
        bubble.setBackground(bg);
        WindowManager.LayoutParams blp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        blp.gravity = Gravity.TOP | Gravity.START;
        // Android 12+ blocks touches that pass through an opaque untouchable window; at 0.8 they reach the app below.
        blp.alpha = 0.8f;
        bubble.setLayoutParams(blp);
        bubble.measure(View.MeasureSpec.makeMeasureSpec((int) (240 * dm.density), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        try {
            wm.addView(bubble, blp);
        } catch (Exception e) {
            bubble = null;
            return;
        }
        positionBubble();
        handler.removeCallbacks(hideBubbleLater);
        handler.postDelayed(hideBubbleLater, 4500);
    }

    private void positionBubble() {
        if (bubble == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        WindowManager.LayoutParams blp = (WindowManager.LayoutParams) bubble.getLayoutParams();
        int w = bubble.getMeasuredWidth(), h = bubble.getMeasuredHeight();
        blp.x = (int) Math.max(4, Math.min(dm.widthPixels - w - 4, x + lp.width / 2f - w / 2f));
        blp.y = (int) (y - h - 4 * dm.density);
        if (blp.y < 0) blp.y = (int) (y + lp.height + 4 * dm.density);
        try { wm.updateViewLayout(bubble, blp); } catch (Exception ignored) { }
    }

    private void hideBubble() {
        handler.removeCallbacks(hideBubbleLater);
        if (bubble != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) { }
            bubble = null;
        }
    }

    // ---------- size & walking ----------

    /** Shrinks to a translucent dot parked at the nearest side, or grows back. */
    private void setMini(boolean m) {
        if (m == mini) return;
        mini = m;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int s = m ? miniSize : size;
        float cx = x + lp.width / 2f, cy = y + lp.height / 2f;
        lp.width = s;
        lp.height = s;
        lp.alpha = m ? 0.6f : 1f;
        y = cy - s / 2f;
        x = m ? (cx < dm.widthPixels / 2f ? 0 : dm.widthPixels - s) : cx - s / 2f;
        clampToScreen();
        tx = x;
        ty = y;
        if (m) hideBubble();
        updatePosition();
        setMoving(false, facing);
        js("setMini(" + m + ")");
        if (!m) startTicking();
    }

    /** Best effort: when a video goes full screen the system bars hide, so shrink out of the way. */
    @SuppressWarnings("deprecation")
    private void watchFullscreen() {
        view.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int vis) {
                boolean full = (vis & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
                if (full && !mini) {
                    autoMini = true;
                    setMini(true);
                } else if (!full && autoMini) {
                    autoMini = false;
                    setMini(false);
                }
            }
        });
    }

    private void pickTarget() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int margin = (int) (8 * dm.density);
        // Short strolls near where it is, so it never wanders far from where she left it.
        float range = 140 * dm.density;
        tx = x + (rnd.nextFloat() * 2 - 1) * range;
        ty = y + (rnd.nextFloat() * 2 - 1) * range * 0.6f;
        tx = Math.max(margin, Math.min(dm.widthPixels - size - margin, tx));
        ty = Math.max(dm.heightPixels * 0.08f, Math.min(dm.heightPixels * 0.85f - size, ty));
    }

    private void setMoving(boolean m, int dir) {
        if (m != moving || dir != facing) {
            moving = m;
            facing = dir;
            js("setMoving(" + m + "," + dir + ")");
        }
    }

    private void startTicking() {
        if (ticking) return;
        ticking = true;
        handler.post(tick);
    }

    /** Walks toward the target while awake; stops itself when asleep or small to save battery. */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!awake || mini || view.getVisibility() != View.VISIBLE) {
                ticking = false;
                setMoving(false, facing);
                return;
            }
            long now = System.currentTimeMillis();
            if (now >= pausedUntil) {
                float dx = tx - x, dy = ty - y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= speed) {
                    x = tx;
                    y = ty;
                    setMoving(false, facing);
                    pausedUntil = now + 2000 + rnd.nextInt(4000);
                    pickTarget();
                } else {
                    x += dx / dist * speed;
                    y += dy / dist * speed;
                    setMoving(true, dx < 0 ? -1 : 1);
                }
                updatePosition();
            }
            handler.postDelayed(this, FRAME_MS);
        }
    };

    private void openToTalk() {
        if (mini) setMini(false);
        js("wave()");
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_LISTEN, true));
    }

    private final Runnable singleTap = new Runnable() {
        @Override
        public void run() {
            if (mini) {
                autoMini = false;
                setMini(false);
                wake(null);
            } else if (!awake) {
                js("wave()");
                wake(String.format(WAKE_LINES[rnd.nextInt(WAKE_LINES.length)], kidName()));
            } else {
                autoMini = false;
                setMini(true);
            }
        }
    };

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            touchConsumed = true;
            if (awake && !mini) sleep(true);
            else if (!mini) { js("wave()"); wake(String.format(WAKE_LINES[rnd.nextInt(WAKE_LINES.length)], kidName())); }
        }
    };
    private boolean touchConsumed = false;

    /** Drag to move, tap to wake/shrink, double tap to talk, long press to sleep. */
    private class TouchHandler implements View.OnTouchListener {
        private float downX, downY, startX, startY;
        private boolean dragging;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = x;
                    startY = y;
                    dragging = false;
                    touchConsumed = false;
                    pausedUntil = Long.MAX_VALUE;
                    setMoving(false, facing);
                    handler.postDelayed(longPress, 800);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float mx = e.getRawX() - downX, my = e.getRawY() - downY;
                    if (!dragging && Math.abs(mx) + Math.abs(my) > 24) {
                        dragging = true;
                        handler.removeCallbacks(longPress);
                    }
                    if (dragging) {
                        x = startX + mx;
                        y = startY + my;
                        clampToScreen();
                        updatePosition();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPress);
                    pausedUntil = System.currentTimeMillis() + 5000;
                    if (dragging && mini) {
                        DisplayMetrics dm = getResources().getDisplayMetrics();
                        x = x + lp.width / 2f < dm.widthPixels / 2f ? 0 : dm.widthPixels - lp.width;
                        updatePosition();
                    }
                    tx = x;
                    ty = y;
                    if (dragging) prefs.edit().putFloat("ovX", x).putFloat("ovY", y).apply();
                    if (!dragging && !touchConsumed && e.getActionMasked() == MotionEvent.ACTION_UP) {
                        long now = System.currentTimeMillis();
                        if (now - lastTapAt < 320) {
                            handler.removeCallbacks(singleTap);
                            lastTapAt = 0;
                            openToTalk();
                        } else {
                            lastTapAt = now;
                            handler.postDelayed(singleTap, 320);
                        }
                    }
                    if (awake) startTicking();
                    return true;
            }
            return false;
        }
    }
}
