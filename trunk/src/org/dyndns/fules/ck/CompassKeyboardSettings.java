package org.dyndns.fules.ck;
import org.dyndns.fules.ck.R;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class CompassKeyboardSettings extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
	private static final String	TAG = "CompassKeyboard";
	PreferenceScreen		layoutPrefs;
	Pattern				indexedExpr;

	@Override protected void onCreate(Bundle b) {
		super.onCreate(b);

		getPreferenceManager().setSharedPreferencesName(CompassKeyboard.SHARED_PREFS_NAME);
		addPreferencesFromResource(R.xml.ck_settings);
		SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
		prefs.registerOnSharedPreferenceChangeListener(this);

		Preference p = findPreference("pg_layouts");
		if ((p == null) || !(p instanceof PreferenceScreen))
			Log.e(TAG, "ck_layouts is null");
		else
			layoutPrefs = (PreferenceScreen)p;

		// A regex for recognising strings like 'something[42]' would be /([^[]*)\[(\d*)\]/, but
		// java.util.regex seems to have troubles with /[^[]/, most probably because Java developers
		// tried to 'invent' a syntax for unions and intersections of character classes like 
		// '[[a-f][0-9]]' and '[[a-z]&&[jkl]]'.
		// They made the '[' a special character inside of character classes, so now it has to be
		// escaped (btw, backslash is NOT a special char in POSIX char classes), and hence the
		// Java-regexes are incompatible with the rest of the world, POSIX included.
		// Well, this was not the best idea. Perhaps reading the POSIX docs first would have been
		// a better approach...
		indexedExpr = Pattern.compile("([^\\[]*)\\[([\\d]*)\\]");

		int n = Integer.parseInt(prefs.getString("ck_num_layouts", "3"));
		//Log.d(TAG, "Number of layouts; n='" + String.valueOf(n) + "'");
		for (int i = 3; i < n; i++) {
			String si = "["+String.valueOf(i)+"]";
			String name = prefs.getString("ck_layout_name"+si, "none");
			String filename = prefs.getString("ck_layout_file"+si, "unnamed");
			Log.d(TAG, "Layout; i='" + String.valueOf(i) + "', name='" + name + "', file='" + filename + "'");

			addLayoutPref("ck_layout_click"+si, i, name, filename);
		}
	}

	@Override protected void onDestroy() {
		getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
	}

	void addLayoutPref(String key, int idx, String name, String filename) {
		TextDialogPreference pp = new TextDialogPreference(this, null);

		pp.setKey(key);
		pp.setTitle(String.valueOf(idx)+": "+name);
		pp.setSummary(filename);
		pp.setEnabled(true);
		pp.setOrder(idx);
		pp.setDialogTitle(R.string.ck_delete_layout_title);
		pp.setDialogMessage(R.string.ck_delete_layout_message);
		pp.setPositiveButtonText(R.string.ck_custom_layout_positive);
		pp.setNegativeButtonText(R.string.ck_custom_layout_negative);
		layoutPrefs.addPreference(pp);
	}

	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		Matcher m = indexedExpr.matcher(key);

		if (m.matches()) {
			String keyName = m.group(1);
			int keyIndex = Integer.parseInt(m.group(2));
			String prefKey = "ck_layout_click["+String.valueOf(keyIndex)+"]";

			if (keyName.contentEquals("ck_layout_file")) {
				Preference p = layoutPrefs.findPreference(prefKey);
				if (p != null)
					p.setSummary(prefs.getString(key, "none"));
				else
					addLayoutPref(prefKey, keyIndex, "unnamed", prefs.getString(key, "none"));
			}
			else if (keyName.contentEquals("ck_layout_name")) {
				Preference p = layoutPrefs.findPreference(prefKey);
				if (p != null)
					p.setTitle(String.valueOf(keyIndex)+": "+prefs.getString(key, "unnamed"));
				else
					addLayoutPref(prefKey, keyIndex, prefs.getString(key, "unnamed"), "none");
			}
			else if (keyName.contentEquals("ck_layout_click"))
			{
				String s;
				try {
					s = prefs.getString(key, "none");
				}
				catch (ClassCastException e) {
					s = "none";
				}
				if (s.contentEquals(TextDialogPreference.POSITIVE)) {
					// remove the layout from the list
					int n = Integer.parseInt(prefs.getString("ck_num_layouts", "3"));
					String sn1 = "["+String.valueOf(n - 1)+"]";

					SharedPreferences.Editor edit = prefs.edit();
					Log.d(TAG, "Removing layout; idx='" + String.valueOf(keyIndex) +"'");
					for (int i = keyIndex + 1; i < n; i++) {
						String si = "["+String.valueOf(i)+"]";
						String si1 = "["+String.valueOf(i - 1)+"]";

						edit.putString("ck_layout_name"+si1, prefs.getString("ck_layout_name"+si, "none"));
						edit.putString("ck_layout_file"+si1, prefs.getString("ck_layout_file"+si, "unnamed"));
					}
					edit.remove("ck_layout_name"+sn1);
					edit.remove("ck_layout_file"+sn1);
					//edit.remove(prefKey);
					edit.remove(key);
					edit.putString("ck_num_layouts", String.valueOf(n - 1));
					edit.putString("ck_layout", "0");
					edit.commit();
					Preference p = layoutPrefs.findPreference("ck_layout_click"+sn1);
					if (p != null)
						layoutPrefs.removePreference(p);
				}
				else {
					// activate the layout
					SharedPreferences.Editor edit = prefs.edit();
					edit.remove(key);
					Log.d(TAG, "Selecting layout; idx='" + String.valueOf(keyIndex) +"'");
					edit.putString("ck_layout", String.valueOf(keyIndex));
					edit.commit();
				}
			}
		}
	}
}

// vim: set ai si sw=8 ts=8 noet:
