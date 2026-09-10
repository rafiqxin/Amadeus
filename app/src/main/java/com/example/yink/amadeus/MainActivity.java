package com.example.yink.amadeus;

/*
 * Big thanks to https://github.com/RIP95 aka Emojikage
 */

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";

    private final VoiceLine[] voiceLines = VoiceLine.Line.getLines();
    private final Random randomgen = new Random();
    private String recogLang;
    private String[] contextLang;
    private SpeechRecognizer sr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        ImageView kurisu = (ImageView) findViewById(R.id.imageView_kurisu);
        ImageView subtitlesBackground = (ImageView) findViewById(R.id.imageView_subtitles);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        recogLang = settings.getString("recognition_lang", "ja-JP");
        contextLang = recogLang.split("-");

        // Do not crash or block the UI when a ROM has no speech recognition
        // provider installed. The Amadeus visual/voice experience remains usable.
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            try {
                sr = SpeechRecognizer.createSpeechRecognizer(this);
                sr.setRecognitionListener(new listener());
            } catch (RuntimeException e) {
                Log.w(TAG, "Speech recognition provider could not be created", e);
                sr = null;
            }
        } else {
            Log.w(TAG, "No SpeechRecognizer provider is available on this device");
            sr = null;
        }

        final Handler handler = new Handler();
        final int REQUEST_PERMISSION_RECORD_AUDIO = 11302;

        if (!settings.getBoolean("show_subtitles", false)) {
            subtitlesBackground.setVisibility(View.INVISIBLE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    MainActivity.this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_PERMISSION_RECORD_AUDIO
            );
        }

        Amadeus.speak(voiceLines[VoiceLine.Line.HELLO], MainActivity.this);

        final Runnable loop = new Runnable() {
            @Override
            public void run() {
                if (Amadeus.isLoop) {
                    Amadeus.speak(voiceLines[randomgen.nextInt(voiceLines.length)], MainActivity.this);
                    handler.postDelayed(this, 5000 + randomgen.nextInt(5) * 1000);
                }
            }
        };

        kurisu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Amadeus.isLoop || Amadeus.isSpeaking) {
                    return;
                }

                int permissionCheck = ContextCompat.checkSelfPermission(
                        MainActivity.this,
                        Manifest.permission.RECORD_AUDIO
                );

                if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                    Amadeus.speak(voiceLines[VoiceLine.Line.DAGA_KOTOWARU], MainActivity.this);
                    return;
                }

                if (sr == null) {
                    // AOSP/LineageOS builds can legitimately have no recognizer.
                    // Keep the app alive and provide the normal fallback voice line.
                    Amadeus.speak(voiceLines[VoiceLine.Line.SORRY], MainActivity.this);
                    return;
                }

                promptSpeechInput();
            }
        });

        kurisu.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (!Amadeus.isLoop && !Amadeus.isSpeaking) {
                    handler.post(loop);
                    Amadeus.isLoop = true;
                } else {
                    handler.removeCallbacks(loop);
                    Amadeus.isLoop = false;
                }
                return true;
            }
        });
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LangContext.wrap(newBase));
    }

    @Override
    protected void onDestroy() {
        if (sr != null) {
            sr.destroy();
            sr = null;
        }
        if (Amadeus.m != null) {
            try {
                Amadeus.m.release();
            } catch (Exception ignored) {
            }
            Amadeus.m = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Amadeus.isLoop = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Amadeus.isLoop = false;
    }

    private void promptSpeechInput() {
        if (sr == null) {
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, recogLang);

        try {
            sr.startListening(intent);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to start speech recognition", e);
            Amadeus.speak(voiceLines[VoiceLine.Line.SORRY], MainActivity.this);
        }
    }

    private class listener implements RecognitionListener {

        private final String TAG = "VoiceListener";

        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "Speech recognition start");
        }

        public void onBeginningOfSpeech() {
            Log.d(TAG, "Listening speech");
        }

        public void onRmsChanged(float rmsdB) {
        }

        public void onBufferReceived(byte[] buffer) {
            Log.d(TAG, "onBufferReceived");
        }

        public void onEndOfSpeech() {
            Log.d(TAG, "Speech recognition end");
        }

        public void onError(int error) {
            Log.d(TAG, "error " + error);
            if (sr != null) {
                sr.cancel();
            }
            Amadeus.speak(voiceLines[VoiceLine.Line.SORRY], MainActivity.this);
        }

        public void onResults(Bundle results) {
            ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (data == null || data.isEmpty()) {
                Amadeus.speak(voiceLines[VoiceLine.Line.SORRY], MainActivity.this);
                return;
            }

            StringBuilder debug = new StringBuilder();
            for (String word : data) {
                debug.append(word).append('\n');
            }
            Log.d(TAG, debug.toString());

            String input = data.get(0);
            String[] splitInput = input.split(" ");

            if (splitInput.length > 0 && splitInput[0].equalsIgnoreCase("Асистент")) {
                splitInput[0] = "Ассистент";
            }

            Context context = LangContext.load(getApplicationContext(), contextLang[0]);

            if (splitInput.length > 2 && splitInput[0].equalsIgnoreCase(context.getString(R.string.assistant))) {
                String cmd = splitInput[1].toLowerCase();
                String[] args = new String[splitInput.length - 2];
                System.arraycopy(splitInput, 2, args, 0, splitInput.length - 2);

                if (cmd.contains(context.getString(R.string.open))) {
                    Amadeus.openApp(args, MainActivity.this);
                }
            } else {
                Amadeus.responseToInput(input, context, MainActivity.this);
            }
        }

        public void onPartialResults(Bundle partialResults) {
            Log.d(TAG, "onPartialResults");
        }

        public void onEvent(int eventType, Bundle params) {
            Log.d(TAG, "onEvent " + eventType);
        }
    }
}
