package com.example.yink.amadeus;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

public class LaunchActivity extends AppCompatActivity {

    private static final String NOTIFICATION_CHANNEL_ID = "amadeus_status";
    private static final int REQUEST_POST_NOTIFICATIONS = 11303;

    private ImageView connect, cancel, logo;
    private TextView status;
    private boolean isPressed = false;
    private MediaPlayer m;
    private final Handler aniHandle = new Handler();

    private int i = 0;

    private final Runnable aniRunnable = new Runnable() {
        @Override
        public void run() {
            final int DURATION = 20;
            if (i < 39) {
                i++;
                String imgName = "logo" + i;
                int id = getResources().getIdentifier(imgName, "drawable", getPackageName());
                if (id != 0) {
                    logo.setImageDrawable(ContextCompat.getDrawable(LaunchActivity.this, id));
                }
                aniHandle.postDelayed(this, DURATION);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        connect = (ImageView) findViewById(R.id.imageView_connect);
        cancel = (ImageView) findViewById(R.id.imageView_cancel);
        status = (TextView) findViewById(R.id.textView_call);
        logo = (ImageView) findViewById(R.id.imageView_logo);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final Window win = getWindow();

        aniHandle.post(aniRunnable);

        if (Alarm.isPlaying()) {
            status.setText(R.string.incoming_call);
            win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        } else {
            status.setText(R.string.call);
        }

        if (settings.getBoolean("show_notification", false)) {
            showNotification();
        }

        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isPressed) {
                    return;
                }

                // Modern Android must not require the Google app package just to
                // enter the Amadeus UI. Speech availability is checked later in
                // MainActivity when the user actually requests recognition.
                isPressed = true;
                connect.setImageResource(R.drawable.connect_select);

                if (Alarm.isPlaying()) {
                    Alarm.cancel(LaunchActivity.this);
                    clearAlarmWindowFlags(win);
                    openMainActivity();
                    return;
                }

                m = MediaPlayer.create(LaunchActivity.this, R.raw.tone);
                if (m == null) {
                    status.setText(R.string.connecting);
                    openMainActivity();
                    return;
                }

                m.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        status.setText(R.string.connecting);
                        mp.start();
                    }
                });

                m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        mp.release();
                        m = null;
                        openMainActivity();
                    }
                });

                m.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        try {
                            mp.release();
                        } catch (Exception ignored) {
                        }
                        m = null;
                        openMainActivity();
                        return true;
                    }
                });
            }
        });

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancel.setImageResource(R.drawable.cancel_select);
                Alarm.cancel(getApplicationContext());
                clearAlarmWindowFlags(win);

                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

        logo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(LaunchActivity.this, SettingsActivity.class));
            }
        });
    }

    private void openMainActivity() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        startActivity(new Intent(LaunchActivity.this, MainActivity.class));
    }

    private void clearAlarmWindowFlags(Window win) {
        win.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LangContext.wrap(newBase));
    }

    @Override
    protected void onDestroy() {
        aniHandle.removeCallbacks(aniRunnable);
        if (m != null) {
            try {
                m.release();
            } catch (Exception ignored) {
            }
            m = null;
        }
        clearAlarmWindowFlags(getWindow());
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Alarm.isPlaying()) {
            status.setText(R.string.incoming_call);
        } else if (isPressed) {
            status.setText(R.string.disconnected);
        } else {
            status.setText(R.string.call);
        }

        isPressed = false;
        connect.setImageResource(R.drawable.connect_unselect);
        cancel.setImageResource(R.drawable.cancel_unselect);
    }

    private int immutablePendingIntentFlags(int baseFlags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return baseFlags | PendingIntent.FLAG_IMMUTABLE;
        }
        return baseFlags;
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null && manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        getString(R.string.app_name),
                        NotificationManager.IMPORTANCE_LOW
                );
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showNotification() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_POST_NOTIFICATIONS
            );
            return;
        }

        ensureNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.xp2)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setAutoCancel(false);

        Intent resultIntent = new Intent(this, MainActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                0,
                immutablePendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        );
        builder.setContentIntent(resultPendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(0, builder.build());
        }
    }
}
