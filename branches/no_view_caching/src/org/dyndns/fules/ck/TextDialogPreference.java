package org.dyndns.fules.ck;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences;
import android.preference.DialogPreference;
import android.util.AttributeSet;
//import android.util.Log;

public class TextDialogPreference extends DialogPreference {
	//private static final String	TAG = "TextDialogPreference";
	public static final String	OPENED = "opened";
	public static final String	POSITIVE = "positive";
	public static final String	NEGATIVE = "negative";

	public TextDialogPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public TextDialogPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	} 

	@Override protected void onClick() {
		super.onClick();
		SharedPreferences.Editor ed = getEditor();
		ed.putString(getKey(), OPENED);
		ed.commit();
	}

	@Override public void onClick(DialogInterface dialog, int which) {
		SharedPreferences.Editor ed = getEditor();
		ed.putString(getKey(), (which == DialogInterface.BUTTON_POSITIVE) ? POSITIVE : NEGATIVE);
		ed.commit();
		notifyChanged();
	}
}

// vim: set ai si sw=8 ts=8 noet:
