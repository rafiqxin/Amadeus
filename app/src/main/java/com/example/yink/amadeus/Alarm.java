package com.example.yink.amadeus;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.PowerManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;

class Alarm {

    private static MediaPlayer m;
    private static Vibrator v;

    static final int ALARM_ID = 104859;
    static final int ALARM_NOTIFICATION_ID = 102434;

    private static final String TAG = "Alarm";
    private static boolean isPlaying = false;
    private static PowerManager.WakeLock sCpuWakeLock;

    private static int immutableFlags(int baseFlags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return baseFlags | PendingIntent.FLAG_IMMUTABLE;
        }
        return baseFlags;
    }

    static void start(Context context, int ringtone) {
        acquireCpuWakeLock(context);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        if (settings.getBoolean("vibrate", false)) {
            v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                long[] pattern = {500, 2000};
                v.vibrate(pattern, 0);
            }
        }

        m = MediaPlayer.create(context, ringtone);
        if (m != null) {
            m.setLooping(true);
            m.start();
            isPlaying = m.isPlaying();
        }

        Log.d(TAG, "Start");
    }

    static void cancel(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        settings.edit().putBoolean("alarm_toggle", false).apply();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent alarmIntent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_ID,
                alarmIntent,
                immutableFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        );
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }

        if (m != null) {
            try {
                if (m.isPlaying()) {
                    m.stop();
                }
                m.release();
            } catch (Exception ignored) {
            }
            m = null;
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(ALARM_NOTIFICATION_ID);
        }

        releaseCpuLock();
        isPlaying = false;

        if (v != null) {
            v.cancel();
            v = null;
        }

        Log.d(TAG, "Cancel");
    }

    static boolean isPlaying() {
        return isPlaying;
    }

    private static void acquireCpuWakeLock(Context context) {
        if (sCpuWakeLock != null) {
            return;
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            sCpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            sCpuWakeLock.acquire(10 * 60 * 1000L);
        }
    }

    private static void releaseCpuLock() {
        if (sCpuWakeLock != null) {
            if (sCpuWakeLock.isHeld()) {
                sCpuWakeLock.release();
            }
            sCpuWakeLock = null;
        }
    }
}
