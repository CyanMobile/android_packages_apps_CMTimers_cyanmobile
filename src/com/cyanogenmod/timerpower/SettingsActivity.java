package com.cyanogenmod.timerpower;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.widget.Toast;

public class SettingsActivity extends PreferenceActivity implements
    OnSharedPreferenceChangeListener {

  public static final String KEY_SHAKE_TO_RESET_PREFERENCE = "Shake_to_reset";
  public static final String KEY_SHOW_HOUR_PREFERENCE = "show_hour_column_preference";

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    addPreferencesFromResource(R.xml.prefernces);
  }

  @Override
  protected void onResume() {
    super.onResume();

    // Set up a listener whenever a key changes
    getPreferenceScreen().getSharedPreferences()
        .registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  protected void onPause() {
    super.onPause();

    // Unregister the listener whenever a key changes
    getPreferenceScreen().getSharedPreferences()
        .unregisterOnSharedPreferenceChangeListener(this);
  }

  /**
   * Show Toasts when preferences change
   */
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
      String key) {
    boolean newValue = false;
    
    if (key.equals(KEY_SHAKE_TO_RESET_PREFERENCE)) {
      newValue = sharedPreferences.getBoolean(key, false);
      Toast.makeText(this,
          getText(R.string.pref_changed_shaketoreset) + " " + String.valueOf(newValue) + ".",
          Toast.LENGTH_SHORT).show();
    } else if (key.equals(KEY_SHOW_HOUR_PREFERENCE)) {
      newValue = sharedPreferences.getBoolean(key, false);
      Toast.makeText(this, 
          getText(R.string.pref_show_hour_column) + " " + String.valueOf(newValue) + ".",
          Toast.LENGTH_SHORT).show();
    }
  }
}
