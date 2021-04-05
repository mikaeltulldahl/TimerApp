package com.example.timerapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

import static java.lang.Math.max;

public class MainActivity extends AppCompatActivity {
    private EditText mTextViewCountDown;
    private Button mButtonStartPause;
    private Button mButtonReset;
    private CountDownTimer mCountDownTimer;
    private boolean mRunning;
    private long mEndTime;
    private SoundPool soundPool;
    private int beepSound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextViewCountDown = findViewById(R.id.text_view_countdown);
        mButtonStartPause = findViewById(R.id.button_start_pause);
        mButtonReset = findViewById(R.id.button_reset);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build();

        beepSound = soundPool.load(this,R.raw.beep,1);

        mTextViewCountDown.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                updateCountDownText();//will remove illegal entries
                updateFocusable(!mRunning);
                updateButtons();
            }
        });

        mButtonStartPause.setOnClickListener(view -> {
            if (mRunning) {
                pauseTime();
            } else {
                startTimer();
            }
        });

        mButtonReset.setOnClickListener(view -> resetTimer());

        //updateCountDownText();
    }

    private void startTimer() {
        updateFocusable(false);
        long currentMillisLeft = millisLeft();
        if (currentMillisLeft > 0) {
            if (!mRunning) { // startTimer may be called multiple times while running. changing mEndTime would be inaccurate
                mEndTime = System.currentTimeMillis() + currentMillisLeft;
            }
            mCountDownTimer = new CountDownTimer(currentMillisLeft, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    updateCountDownText();
                }

                @Override
                public void onFinish() {
                    updateCountDownText();
                    mRunning = false;
                    updateButtons();
                    soundPool.play(beepSound,1,1,0,0,1);
                    updateFocusable(true);
                }
            }.start();
            mRunning = true;
        }
        updateButtons();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        soundPool.release();
        soundPool = null;
    }

    private void pauseTime() {
        mCountDownTimer.cancel();
        mRunning = false;
        updateFocusable(true);
        updateButtons();
    }

    private void updateFocusable(boolean focusable){
        if(focusable){
            mTextViewCountDown.setInputType(InputType.TYPE_DATETIME_VARIATION_TIME);
            mTextViewCountDown.setFocusableInTouchMode(true);
        }else{
            mTextViewCountDown.endBatchEdit();
            mTextViewCountDown.setFocusable(false);
            mTextViewCountDown.setInputType(InputType.TYPE_NULL);
        }
    }

    private void setTime(String newTime) {
        if (mRunning) {
            pauseTime();
        }
        mTextViewCountDown.setText(newTime);
        updateButtons();
        mButtonStartPause.setEnabled(millisLeft() > 0);
    }

    private void resetTimer() {
        setTime(getResources().getString(R.string.default_time));
    }

    private void updateCountDownText() {
        long currentMillisLeft = max(0, millisLeft());
        int secondsRoundedUp = (int) Math.ceil((double) currentMillisLeft / 1000);
        int minutes = secondsRoundedUp / 60;
        int seconds = (secondsRoundedUp % 60);
        String timeLeft = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        if (!timeLeft.equals(mTextViewCountDown.getText().toString())) {
            mTextViewCountDown.setText(timeLeft);
        }
    }

    private void updateButtons() {
        if (mRunning) {
            mButtonStartPause.setText(R.string.pause);
            mButtonReset.setEnabled(true);
        } else {
            mButtonStartPause.setText(R.string.start);
            mButtonReset.setEnabled(!mTextViewCountDown.getText().toString().equals(getResources().getString(R.string.default_time)));
            mButtonStartPause.setEnabled(millisLeft() > 0);
        }
    }

    public long millisLeft() {
        if (mRunning) {
            return Math.max(0, mEndTime - System.currentTimeMillis());
        } else {
            return timeString2Millis(mTextViewCountDown.getText().toString());
        }
    }

    public long timeString2Millis(String time) {
        String minutesString;
        String secondsString;
        int colonIdx = time.indexOf(":");
        if(colonIdx == -1){//no colon, assume seconds-only input
            minutesString = "0";
            secondsString = time;
        }else {
            minutesString = time.substring(0, colonIdx);
            secondsString = time.substring(colonIdx + 1);
        }
        int minutes = 0;
        int seconds = 0;
        try {
            minutes = Integer.parseInt(minutesString);
        } catch (NumberFormatException ignored) { }
        try {
            seconds = Integer.parseInt(secondsString);
        } catch (NumberFormatException ignored) { }
        return Math.max(0, 1000 * (seconds + 60 * minutes));
    }

    @Override
    protected void onStop() {
        super.onStop();
        SharedPreferences.Editor editor = getSharedPreferences("prefs", MODE_PRIVATE).edit();
        editor.putBoolean("isRunning", mRunning);
        editor.putLong("endTime", mEndTime);
        editor.putString("timeLeft", mTextViewCountDown.getText().toString());
        editor.apply();
        mCountDownTimer.cancel();
    }

    @Override
    protected void onStart() {
        super.onStart();
        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String newTime = prefs.getString("timeLeft", getResources().getString(R.string.default_time));
        mEndTime = prefs.getLong("endTime", 0);
        mRunning = prefs.getBoolean("isRunning", false);
        if (millisLeft() <= 0) {
            mRunning = false;
            newTime = getResources().getString(R.string.zero_time);
        }
        mTextViewCountDown.setText(newTime);
        updateCountDownText();
        updateFocusable(!mRunning);
        updateButtons();
        if (mRunning) {
            startTimer();
        }
    }

    //make touches outside editText lose focus
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if ( v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int)event.getRawX(), (int)event.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent( event );
    }
}