package org.dyndns.fules.ck;
import org.dyndns.fules.ck.R;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class CompassKeyboardSettings extends PreferenceActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getPreferenceManager().setSharedPreferencesName(CompassKeyboard.SHARED_PREFS_NAME);
        addPreferencesFromResource(R.xml.ck_settings);
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    }
}
