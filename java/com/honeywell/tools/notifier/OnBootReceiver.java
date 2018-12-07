package com.honeywell.tools.notifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by E438447 on 2/8/2017.
 */
public class OnBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent intentLauncher = new Intent(context, MainActivity.class);
            context.startActivity(intentLauncher);
        }
    }
}
