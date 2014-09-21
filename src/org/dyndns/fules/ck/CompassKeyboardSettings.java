package org.dyndns.fules.ck;
import org.dyndns.fules.ck.R;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;
//import android.preference.Preference;
//import android.preference.PreferenceScreen;

public class CompassKeyboardSettings extends PreferenceActivity {
	private static final String	TAG = "CompassKeyboard";

	@Override protected void onCreate(Bundle b) {
		super.onCreate(b);

		getPreferenceManager().setSharedPreferencesName(CompassKeyboard.SHARED_PREFS_NAME);
		addPreferencesFromResource(R.xml.ck_settings);
	}

	@Override protected void onDestroy() {
		super.onDestroy();
	}
}

// vim: set ai si sw=8 ts=8 noet:
