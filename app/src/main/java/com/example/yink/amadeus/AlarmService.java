package com.example.yink.amadeus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

public class AlarmService extends Service {

    public static final String EXTRA_RINGTONE = "amadeus.extra.RINGTONE";
    private static final String CHANNEL_ID = "amadeus_alarm";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int ringtone = R.raw.ringtone_gate_of_steiner;
        if (intent != null) {
            ringtone = intent.getIntExtra(EXTRA_RINGTONE, ringtone);
        }

        ensureNotificationChannel();
        Notification notification = buildAlarmNotification();
        startForeground(Alarm.ALARM_NOTIFICATION_ID, notification);

        if (!Alarm.isPlaying()) {
            Alarm.start(this, ringtone);
        }

        return START_NOT_STICKY;
    }

    private int immutableFlags(int baseFlags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return baseFlags | PendingIntent.FLAG_IMMUTABLE;
        }
        return baseFlags;
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.app_name) + " Alarm",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription(getString(R.string.incoming_call));
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildAlarmNotification() {
        Intent launchIntent = new Intent(this, LaunchActivity.class);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent launchPendingIntent = PendingIntent.getActivity(
                this,
                Alarm.ALARM_ID,
                launchIntent,
                immutableFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.incoming_call)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.incoming_call))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.incoming_call)))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(launchPendingIntent)
                .setFullScreenIntent(launchPendingIntent, true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
