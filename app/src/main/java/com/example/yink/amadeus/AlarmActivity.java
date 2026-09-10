package com.example.yink.amadeus;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.Calendar;

public class AlarmActivity extends AppCompatActivity {

    private static final String TAG = "AlarmActivity";
    private static final int REQUEST_POST_NOTIFICATIONS = 11304;

    private AlarmManager alarmManager;
    private PendingIntent pendingIntent;
    private TimePicker alarmTimePicker;
    private ToggleButton alarmToggle;
    private SharedPreferences settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        alarmTimePicker = (TimePicker) findViewById(R.id.alarmTimePicker);
        alarmToggle = (ToggleButton) findViewById(R.id.alarmToggle);
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        pendingIntent = PendingIntent.getBroadcast(
                this,
                Alarm.ALARM_ID,
                new Intent(this, AlarmReceiver.class),
                immutableFlags(PendingIntent.FLAG_CANCEL_CURRENT)
        );

        alarmTimePicker.setIs24HourView(settings.getBoolean("24-hour_format", true));
        alarmToggle.setChecked(settings.getBoolean("alarm_toggle", false));
    }

    private int immutableFlags(int baseFlags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return baseFlags | PendingIntent.FLAG_IMMUTABLE;
        }
        return baseFlags;
    }

    public void onToggleClicked(View view) {
        SharedPreferences.Editor editor = settings.edit();

        if (!alarmToggle.isChecked()) {
            Alarm.cancel(this);
            editor.putBoolean("alarm_toggle", false).apply();
            Log.d(TAG, "Alarm Off");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && alarmManager != null
                && !alarmManager.canScheduleExactAlarms()) {
            alarmToggle.setChecked(false);
            editor.putBoolean("alarm_toggle", false).apply();
            Toast.makeText(this, "Allow Amadeus to set alarms and reminders, then enable the alarm again.", Toast.LENGTH_LONG).show();
            try {
                Intent permissionIntent = new Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())
                );
                startActivity(permissionIntent);
            } catch (Exception e) {
                Log.w(TAG, "Unable to open exact-alarm settings", e);
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_POST_NOTIFICATIONS
            );
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            calendar.set(Calendar.HOUR_OF_DAY, alarmTimePicker.getHour());
            calendar.set(Calendar.MINUTE, alarmTimePicker.getMinute());
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, alarmTimePicker.getCurrentHour());
            calendar.set(Calendar.MINUTE, alarmTimePicker.getCurrentMinute());
        }

        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DATE, 1);
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Exact alarm permission rejected", e);
            alarmToggle.setChecked(false);
            editor.putBoolean("alarm_toggle", false).apply();
            Toast.makeText(this, "Android did not grant exact alarm access.", Toast.LENGTH_LONG).show();
            return;
        }

        editor.putBoolean("alarm_toggle", true);
        editor.putLong("alarm_time", calendar.getTimeInMillis());
        editor.apply();

        Toast.makeText(
                this,
                "Alarm set for " + String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)),
                Toast.LENGTH_SHORT
        ).show();
        Log.d(TAG, "Alarm On");
    }
}
