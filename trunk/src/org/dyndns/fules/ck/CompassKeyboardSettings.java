package org.dyndns.fules.ck;
import org.dyndns.fules.ck.R;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.preference.Preference;
import android.preference.ListPreference;
//import android.preference.PreferenceScreen;

public class CompassKeyboardSettings extends PreferenceActivity {
	private static final String	TAG = "CompassKeyboard";

	public CharSequence[] myCopyOf(CharSequence[] x, int n) { // java.utils.Arrays.copyOf() is missing in API < 9
		CharSequence[] result = new CharSequence[n];
		for (int i = 0; i < n; i++)
			result[i] = (i < x.length) ? x[i] : null;
		return result;
	}


	@Override protected void onCreate(Bundle b) {
		super.onCreate(b);

		getPreferenceManager().setSharedPreferencesName(CompassKeyboard.SHARED_PREFS_NAME);
		addPreferencesFromResource(R.xml.ck_settings);

		Preference lp = findPreference("layout");
		if (lp == null)
			Log.d(TAG, "Cannot find preference 'layout'");
		else if (!(lp instanceof ListPreference))
			Log.d(TAG, "Found preference 'layout' but it's not a ListPreference");
		else {
			ListPreference llp = (ListPreference)lp;
			int num_entries = llp.getEntries().length;
			CharSequence[] entries = myCopyOf(llp.getEntries(), num_entries + 1);
			CharSequence[] entryValues = myCopyOf(llp.getEntryValues(), num_entries + 1);
			entries[num_entries] = "YaddaBoo";
			entryValues[num_entries] = "/mnt/sdcard/Document/test.xml";

			llp.setEntries(entries);
			llp.setEntryValues(entryValues);
		}
		// find ListPreference key="layout"
		// add: entries[+]="nesze" entryValues[+]="yaddaboo"

	}

	@Override protected void onDestroy() {
		super.onDestroy();
	}
}

// vim: set ai si sw=8 ts=8 noet:
