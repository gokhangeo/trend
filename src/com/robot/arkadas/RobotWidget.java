package com.robot.arkadas;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Home-screen widget: tap the robot to open the app and start talking right away. */
public class RobotWidget extends AppWidgetProvider {
    /** Re-draws placed widgets, e.g. after the robot was renamed. */
    public static void refresh(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, RobotWidget.class));
        if (ids.length > 0) new RobotWidget().onUpdate(ctx, mgr, ids);
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        Intent talk = new Intent(ctx, MainActivity.class)
                .putExtra(MainActivity.EXTRA_LISTEN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 2, talk,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String name = ctx.getSharedPreferences("robot", Context.MODE_PRIVATE).getString("botName", "Gizem");
        for (int id : ids) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
            rv.setTextViewText(R.id.widget_label, name + " ile konuş");
            rv.setOnClickPendingIntent(R.id.widget_root, pi);
            mgr.updateAppWidget(id, rv);
        }
    }
}
