package com.cyanogenmod.timerpower;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import com.cyanogenmod.timerpower.utils.CMDProcessor;
import com.cyanogenmod.timerpower.utils.Helpers;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * Count down Timer activity.
 * 
 * @author nwolf@google.com (Noam Wolf)
 */
public class ClockApps extends Activity {

  private static final String PREFS_CURRENT_DISPLAY_SECONDS = "displaySeconds";
  private static final String PREFS_CURRENT_DISPLAY_MINUTES = "displayMinutes";
  private static final String PREFERENCE_INITIAL_DISPLAY_SECONDS = "preference_initial_displaySeconds";
  private static final String PREFERENCE_INITIAL_DISPLAY_MINUTES = "preference_initial_displayMinutes";
  private static final String PERSIST_IS_RUNNING = "isRunning";
  private static final String PERSIST_STOP_MARKER_MILLIS = "stopMarkerMillis";
  private static final String PREFS_CURRENT_DISPLAY_HOURS = "displayHours";
  private static final String PREFERENCE_INITIAL_DISPLAY_HOURS = "preference_initial_displayHours";

  private boolean isRunning;
  private TextView displayMinutes, displaySeconds, displayHours;
  private ImageButton btnAddMinute, btnSubtractMinute, btnAddSecond,
      btnSubtractSecond, btnAddHour, btnSubtractHour;
  private Button btnStart, btnReset;
  private int touchBuffer;
  private boolean isShakeToReset = true;

  private SensorManager sensorManager;
  private SharedPreferences sharedPreferences;
  private static MyCountDownTimer timer;
  private PendingIntent sender;
  Handler mHandler;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mHandler = new Handler();

    setContentView(R.layout.main);
    setTitle(R.string.title_countdown_timer);

    displayMinutes = (TextView) findViewById(R.id.displayMinutes);
    displaySeconds = (TextView) findViewById(R.id.displaySeconds);
    displayHours = (TextView) findViewById(R.id.displayHours);

    btnStart = (Button) findViewById(R.id.start_button);
    btnStart.setOnClickListener(startClickListener);

    btnReset = (Button) findViewById(R.id.reset_button);
    btnReset.setOnClickListener(resetClickListener);
    btnReset.setOnLongClickListener(new OnLongClickListener() {

      public boolean onLongClick(View v) {
        done();
        setDisplay("0", "0", "0");
        return true;
      }
    });

    btnAddMinute = (ImageButton) findViewById(R.id.add_minutes);
    btnAddSecond = (ImageButton) findViewById(R.id.add_seconds);
    btnSubtractMinute = (ImageButton) findViewById(R.id.subtract_minutes);
    btnSubtractSecond = (ImageButton) findViewById(R.id.subtract_seconds);
    btnAddHour = (ImageButton) findViewById(R.id.add_hours);
    btnSubtractHour = (ImageButton) findViewById(R.id.subtract_hours);

    setListeners(btnAddHour, btnAddMinute, btnAddSecond, btnSubtractMinute,
        btnSubtractSecond, btnSubtractHour);

    done();
  }

  private void setHourVisibility() {
    if (sharedPreferences == null) {
      sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }
    
    boolean show = sharedPreferences.getBoolean(SettingsActivity.KEY_SHOW_HOUR_PREFERENCE, true);
    
    int visibility = show ? View.VISIBLE : View.GONE;
    displayHours.setVisibility(visibility);
    btnAddHour.setVisibility(visibility);
    btnSubtractHour.setVisibility(visibility);
    TextView separator = (TextView) findViewById(R.id.hour_separator);
    separator.setVisibility(visibility);
    
    if (!show)  {
      SharedPreferences.Editor editor = getPreferences(0).edit();
      editor.putString(PREFS_CURRENT_DISPLAY_HOURS, "0");
      editor.putString(PREFERENCE_INITIAL_DISPLAY_HOURS, "0");
      editor.commit();
      displayHours.setText("0");
    }
  }

  private void setListeners(ImageButton... imagebuttons) {
    for (ImageButton imageButton : imagebuttons) {
      imageButton.setOnClickListener(adjustTimeClickListener);
      imageButton.setOnTouchListener(adjustTouchTimeListener);
    }
  }

  private void done() {
    if (timer != null) {
      timer.cancel();
    }
    toggleStartButton(true);
    isRunning = false;
  }

  private OnClickListener startClickListener = new OnClickListener() {
    public void onClick(View v) {
      saveInitialDisplay();
      beginTimer();
      toggleStartButton(false);
    }
  };

  private OnClickListener stopClickListener = new OnClickListener() {
    public void onClick(View v) {
      done();
    }
  };

  private void toggleStartButton(boolean isOn) {
    btnReset.setEnabled(isOn);
    if (isOn) {
      btnStart.setText(R.string.start);
      btnStart.setOnClickListener(startClickListener);
      setImageButtonSrc(true);
    } else {
      btnStart.setText(R.string.stop);
      btnStart.setOnClickListener(stopClickListener);
      setImageButtonSrc(false);
    }
  }

  private void setImageButtonSrc(boolean enableButtons) {
    List<ImageButton> imageButtons = Arrays.asList(btnAddMinute, btnAddSecond, btnAddHour);
    for (ImageButton imageButton : imageButtons) {
      imageButton.setImageResource(enableButtons ? R.drawable.up
          : R.drawable.up_disabled);
      imageButton.setEnabled(enableButtons);
    }
    imageButtons = Arrays.asList(btnSubtractMinute, btnSubtractSecond, btnSubtractHour);
    for (ImageButton imageButton : imageButtons) {
      imageButton.setImageResource(enableButtons ? R.drawable.down
          : R.drawable.down_disabled);
      imageButton.setEnabled(enableButtons);
    }
  }

  private OnClickListener resetClickListener = new OnClickListener() {
    public void onClick(View v) {
      reset();
    }
  };

  /**
   *  ACTION_DOWN = 0;
   *  ACTION_UP = 1;
   *  ACTION_MOVE = 2;
   *  ACTION_CANCEL = 3;
   */
  private OnTouchListener adjustTouchTimeListener = new OnTouchListener() {

    public boolean onTouch(View v, MotionEvent event) {
      if (event.getAction() == MotionEvent.ACTION_MOVE) {
        touchBuffer++;
        if (touchBuffer > 8) {
          adjustTimer(v, 1);
        }
        return true;        
      }
      touchBuffer = 0;
      return false;
    }

  };

  private OnClickListener adjustTimeClickListener = new OnClickListener() {
    public void onClick(View v) {
      adjustTimer(v, 1);
    }
  };

  private void reset() {
    done();
    restoreDisplay();    
  }

  private void adjustTimer(View v, int delta) {
    touchBuffer = 0;
    int hours = Integer.parseInt(displayHours.getText().toString());
    int mins = Integer.parseInt(displayMinutes.getText().toString());
    int secs = Integer.parseInt(displaySeconds.getText().toString());
    int topLimit = 60 - delta;
    int bottomLimit = delta - 1;

    switch (v.getId()) {
      case R.id.add_hours:
        if (hours < topLimit) {
          hours += delta;
        } else {
          hours = 0;
        }
        break;
      case R.id.add_minutes:
        if (mins < topLimit) {
          mins += delta;
        } else {
          mins = 0;
        }
        break;
      case R.id.add_seconds:
        if (secs < topLimit) {
          secs += delta;
        } else {
          secs = 0;
        }
        break;
      case R.id.subtract_hours:
        if (hours > bottomLimit) {
          hours -= delta;
        } else {
          hours = 59;
        }
        break;
      case R.id.subtract_minutes:
        if (mins > bottomLimit) {
          mins -= delta;
        } else {
          mins = 59;
        }
        break;
      case R.id.subtract_seconds:
        if (secs > bottomLimit) {
          secs -= delta;
        } else {
          secs = 59;
        }
        break;
    }

    String displayMinutes = String.valueOf(mins);
    String displaySeconds = String.valueOf(secs);
    String displayHours = String.valueOf(hours);
    setDisplay(displayHours, displayMinutes, displaySeconds);
  }

  private void showErrorDialog(String message) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage(message);
    builder.create().show();
  }

  private void beginTimer() {
    String hours = displayHours.getText().toString().trim();
    String mins = displayMinutes.getText().toString().trim();
    String secs = displaySeconds.getText().toString().trim();
    resumeTimer(hours, mins, secs);
  }

  private void resumeTimer(int hours, int mins, int secs) {
    resumeTimer(String.valueOf(hours), String.valueOf(mins), String.valueOf(secs));
  }
  /**
   * News up a CountDownTimer.
   * @param hours 
   * 
   */
  private void resumeTimer(String hours, String mins, String secs) {
    setDisplay(hours, mins, secs);
    long totalMilis;
    try {
      totalMilis = (Integer.parseInt(hours) * 3600 * 1000) + (Integer.parseInt(mins) * 60 * 1000)
          + (Integer.parseInt(secs) * 1000);
      if (timer != null) {
        timer.cancel();
        timer = null;
      }
      timer = new MyCountDownTimer(totalMilis, 1000);
      timer.start();
      isRunning = true;
    } catch (NumberFormatException e) {
      showErrorDialog("Invalid Number.");
    }
  }

  public void setDisplay(String hours, String minutes, String seconds) {
    // just in case
    if (hours.contains("-") || minutes.contains("-") || seconds.contains("-")) {
      hours = minutes = seconds = "0";
    }
    displayHours.setText(String.format("%02d", Integer.parseInt(hours)));
    displayMinutes.setText(String.format("%02d", Integer.parseInt(minutes)));
    displaySeconds.setText(String.format("%02d", Integer.parseInt(seconds)));
  }

  private SensorListener sensorListener = new SensorListener() {
    public float accelerometer_shake_threshold = 50000;
    static final int ACCELEROMETER_FLOAT_TO_INT = 1024;

    public void onAccuracyChanged(int sensor, int accuracy) {
    }

    public void onSensorChanged(int sensor, float[] values) {
      synchronized (this) {
        if (sensor == SensorManager.SENSOR_ACCELEROMETER) {

          int ax = (int) (ACCELEROMETER_FLOAT_TO_INT * values[0]);
          int ay = (int) (ACCELEROMETER_FLOAT_TO_INT * values[1]);
          int az = (int) (ACCELEROMETER_FLOAT_TO_INT * values[2]);

          int len2 = (ax * ax + ay * ay + az * az) / 1000;

          if (len2 < accelerometer_shake_threshold) {
            if (!isRunning) {
              Toast.makeText(ClockApps.this, R.string.reset_due_to_shake, Toast.LENGTH_SHORT).show();
              reset();
            }
          }
        } else if (sensor == SensorManager.SENSOR_ORIENTATION) {
          // TODO figure out how to change layout on horizontal orientation
        }
      }
    }

  };

  /***
   * 
   * 
   * Manage state
   * 
   */
  @Override
  protected void onResume() {
    Log.e("clock_apps", "on resume");
    setHourVisibility();
    super.onResume();

    SharedPreferences prefs = getPreferences(0);
    boolean restoredIsRunning = prefs.getBoolean(PERSIST_IS_RUNNING, false);
    String restoredHoursText = prefs.getString(PREFS_CURRENT_DISPLAY_HOURS, "0");
    String restoredMinutesText = prefs.getString(PREFS_CURRENT_DISPLAY_MINUTES, "0");
    String restoredSecondsText = prefs.getString(PREFS_CURRENT_DISPLAY_SECONDS, "0");
    if (restoredIsRunning) {
      long stopMarker = prefs.getLong(PERSIST_STOP_MARKER_MILLIS, 0);

      // Handle time
      if (stopMarker > 0) {
        int hours = Integer.parseInt(restoredHoursText);
        int minutes = Integer.parseInt(restoredMinutesText);
        int seconds = Integer.parseInt(restoredSecondsText);
        
        Calendar nowCalendar = Calendar.getInstance();
        nowCalendar.setTimeInMillis(System.currentTimeMillis());
        Calendar stopedCalendar = Calendar.getInstance();
        stopedCalendar.setTimeInMillis(stopMarker);
        
        // get elapsed calendar
        nowCalendar.add(Calendar.HOUR, stopedCalendar.get(Calendar.HOUR_OF_DAY) * -1);
        nowCalendar.add(Calendar.MINUTE, stopedCalendar.get(Calendar.MINUTE) * -1);
        nowCalendar.add(Calendar.SECOND, stopedCalendar.get(Calendar.SECOND) * -1);
        
        int elapsedHours = nowCalendar.get(Calendar.HOUR);
        int elapsedMinutes = nowCalendar.get(Calendar.MINUTE);
        int elapsedSeconds = nowCalendar.get(Calendar.SECOND);
        
        if (elapsedSeconds > seconds) {
          seconds += 60;
          minutes -= 1;
        }

        if (elapsedMinutes > minutes) {
          minutes += 60;
          hours -= 1;
        }
    
        hours = (int) (hours - elapsedHours);
        minutes = (int) (minutes - elapsedMinutes);
        seconds = (int) (seconds - elapsedSeconds);
        
        resumeTimer(hours, minutes, seconds);
        toggleStartButton(false);
      }
    } else {
      restoreDisplay();
    }

    // Handle shake to reset
    if (sharedPreferences == null) {
      sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ClockApps.this);
    }
    isShakeToReset = sharedPreferences.getBoolean(
        SettingsActivity.KEY_SHAKE_TO_RESET_PREFERENCE, true);

    if (isShakeToReset) {
      sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
      sensorManager.registerListener(sensorListener,
          SensorManager.SENSOR_ACCELEROMETER);
    }
  }

  private void restoreDisplay() {
    SharedPreferences prefs = getPreferences(0);
    setDisplay(prefs.getString(PREFERENCE_INITIAL_DISPLAY_HOURS, "0"),
        prefs.getString(PREFERENCE_INITIAL_DISPLAY_MINUTES, "0"),
        prefs.getString(PREFERENCE_INITIAL_DISPLAY_SECONDS, "0"));
  }

  @Override
  protected void onStop() {
    if (sensorManager != null) {
      sensorManager.unregisterListener(sensorListener);
    }
    saveDisplay();
    super.onStop();
  }

  @Override
  protected void onPause() {
    saveDisplay();
    super.onPause();
  }

  private void saveInitialDisplay() {
    SharedPreferences.Editor editor = getPreferences(0).edit();
    editor.putString(PREFERENCE_INITIAL_DISPLAY_MINUTES,
        displayMinutes.getText().toString());
    editor.putString(PREFERENCE_INITIAL_DISPLAY_SECONDS,
        displaySeconds.getText().toString());
    editor.putString(PREFERENCE_INITIAL_DISPLAY_HOURS,
        displayHours.getText().toString());
    editor.commit();
  }

  private void saveDisplay() {
    SharedPreferences.Editor editor = getPreferences(0).edit();
    editor.putBoolean(PERSIST_IS_RUNNING, isRunning);
    editor.putString(PREFS_CURRENT_DISPLAY_HOURS, displayHours.getText().toString());
    editor.putString(PREFS_CURRENT_DISPLAY_MINUTES, displayMinutes.getText().toString());
    editor.putString(PREFS_CURRENT_DISPLAY_SECONDS, displaySeconds.getText().toString());
    editor.putLong(PERSIST_STOP_MARKER_MILLIS, System.currentTimeMillis());
    editor.commit();
  }

  private class MyCountDownTimer extends CountDownTimer {

    public MyCountDownTimer(long millisInFuture, long countDownInterval) {
      super(millisInFuture, countDownInterval);
    }

    @Override
    public void onFinish() {
      Log.e("onfinish", "onfinish called");
      if (isRunning) {
        reset();
        mHandler.postDelayed(mShutts, 180);
      }
    }

    Runnable mShutts = new Runnable() {
        public void run() {
            readyToShutDown();
        }
    };

    private void readyToShutDown() {
        final CMDProcessor cmd = new CMDProcessor();
        cmd.su.runWaitFor("poweroff");
    }

    @Override
    public void onTick(long millisUntilFinished) {
      displayTime(millisUntilFinished);
    }

    private void displayTime(long millisUntilFinished) {

      long time = millisUntilFinished / 1000;
      long hours = time / 3600;
      long mins = (time % 3600) / 60;
      long secs = time % 60;

      setDisplay(String.valueOf(hours), String.valueOf(mins), String.valueOf(secs));
    }

  }
  
  /**
   * Menu stuff
   */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.menu_items, menu);
    menu.findItem(R.id.menu_countdown_timer).setEnabled(false);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getTitle().equals("Settings")) {
      final Intent intent = new Intent();
      intent.setClass(ClockApps.this, SettingsActivity.class);
      startActivity(intent);
      return true;
    } else if (item.getTitle().equals("Global Time")) {
      final Intent intent = new Intent();
      intent.setClass(ClockApps.this, SettingsActivity.class);
      startActivity(intent);
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

}
