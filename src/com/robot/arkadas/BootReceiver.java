package com.robot.arkadas;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Brings the floating robot back after the phone restarts or the app is updated. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        MainActivity.ensureOverlay(ctx);
    }
}
