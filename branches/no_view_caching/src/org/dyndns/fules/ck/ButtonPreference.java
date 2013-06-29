package org.dyndns.fules.ck;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;

public class ButtonPreference extends Preference {
	private static final String	TAG = "CompassKeyboard";
	boolean value = true;

	public ButtonPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ButtonPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override protected void onClick() {
		super.onClick();
		if (!callChangeListener(value))
			return;
		persistBoolean(value);
	}

	/*@Override protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
		super.onSetInitialValue(restorePersistedValue, defaultValue);
	} */
}

// vim: set ai si sw=8 ts=8 noet:
