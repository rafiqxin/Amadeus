package com.example.yink.amadeus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

public class AlarmBootReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmBootReceiver";

    private static int immutableFlags(int baseFlags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return baseFlags | PendingIntent.FLAG_IMMUTABLE;
        }
        return baseFlags;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        if (!settings.getBoolean("alarm_toggle", false)) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Alarm restore skipped: exact alarm access is not granted");
            return;
        }

        long alarmTime = settings.getLong("alarm_time", 0L);
        if (alarmTime <= System.currentTimeMillis()) {
            Log.w(TAG, "Alarm restore skipped: stored alarm time is already in the past");
            settings.edit().putBoolean("alarm_toggle", false).apply();
            return;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                Alarm.ALARM_ID,
                new Intent(context, AlarmReceiver.class),
                immutableFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        alarmTime,
                        pendingIntent
                );
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
            }
            Log.d(TAG, "Alarm has been recovered");
        } catch (SecurityException e) {
            Log.e(TAG, "Unable to restore exact alarm", e);
        }
    }
}
