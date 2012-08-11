package com.cyanogenmod.timerpower;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

public class TimeSelectionPreference extends Preference {

  TextView displayMinutes, displaySeconds;
  
  public TimeSelectionPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    
    setWidgetLayoutResource(R.layout.time_selection_preference);
    
  }
  
  @Override
  protected void onBindView(View view) {
      super.onBindView(view);
      displayMinutes = (TextView) view.findViewById(R.id.displayMinutes);
      if (displayMinutes != null) {
        
      }
  }

  @Override
  protected void onClick() {
    notifyChanged();
  }

}
