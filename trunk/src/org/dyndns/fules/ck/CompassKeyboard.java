package org.dyndns.fules.ck;
import org.dyndns.fules.ck.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.inputmethodservice.KeyboardView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.R.id;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.inputmethodservice.AbstractInputMethodService;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.Iterator;

public class CompassKeyboard extends InputMethodService implements KeyboardView.OnKeyboardActionListener, SharedPreferences.OnSharedPreferenceChangeListener  {
	public static final String	SHARED_PREFS_NAME = "CompassKeyboardSettings";
	public static final int[]	builtinLayouts = { R.xml.default_latin, R.xml.default_cyrillic, R.xml.default_greek }; // keep in sync with constants.xml
	private static final String	TAG = "CompassKeyboard";

	private SharedPreferences	mPrefs;					// the preferences instance
	CompassKeyboardView		ckv = null;				// the current layout view, either @ckvHorizontal or @ckvVertical
	boolean				forcePortrait;				// use the portrait layout even for horizontal screens

	ExtractedTextRequest		etreq = new ExtractedTextRequest();
	int				selectionStart = -1, selectionEnd = -1;
	int				currentLayout = -1;

	// send an auto-revoked notification with a title and a message
	void sendNotification(String title, String msg) {
		// Simple as a pie, isn't it...
		Notification n = new Notification(android.R.drawable.ic_notification_clear_all, title, System.currentTimeMillis());
		n.flags = Notification.FLAG_AUTO_CANCEL;
		n.setLatestEventInfo(this, title, msg, PendingIntent.getActivity(this, 0, new Intent(), 0));
		((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, n);
		Log.e(TAG, title+"; "+msg);
	}

	public String getLayoutName(XmlPullParser parser) throws XmlPullParserException, java.io.IOException {
		while (parser.getEventType() == XmlPullParser.START_DOCUMENT)
			parser.next();

		if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("CompassKeyboard"))
			throw new XmlPullParserException("Expected <CompassKeyboard>", parser, null);

		return parser.getAttributeValue(null, "name");
	}

	public XmlPullParser openLayout(String filename) throws XmlPullParserException, java.io.IOException {
		FileInputStream is = new FileInputStream(filename);
		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
		factory.setNamespaceAware(false);
		XmlPullParser parser = factory.newPullParser();
		parser.setInput(is, null);
		return parser;
	}

	public XmlPullParser openLayout(int i) {
		Log.d(TAG, "Loading layout '"+String.valueOf(i)+"'");
		switch (i) {
			case 0:
				return getResources().getXml(R.xml.default_latin);

			case 1:
				return getResources().getXml(R.xml.default_cyrillic);

			case 2:
				return getResources().getXml(R.xml.default_greek);
		}

		String err = null;
		try {
			String s = mPrefs.getString("ck_layout_file["+String.valueOf(i)+"]", "");
			if ((s == null) || s.contentEquals(""))
				throw new FileNotFoundException("Invalid Layout index '"+String.valueOf(i)+"'");
			return openLayout(s);
		}
		catch (FileNotFoundException e)		{ err = e.getMessage(); }
		catch (XmlPullParserException e)	{ err = e.getMessage(); }
		catch (java.io.IOException e)		{ err = e.getMessage(); }
		sendNotification("Invalid layout", err);
		return getResources().getXml(R.xml.default_latin); // revert to default latin
	}

	@Override public AbstractInputMethodService.AbstractInputMethodImpl onCreateInputMethodInterface() {
		Log.d(TAG, "onCreateInputMethodInterface;");
		mPrefs = getSharedPreferences(SHARED_PREFS_NAME, 0);
		etreq.hintMaxChars = etreq.hintMaxLines = 0;

		return super.onCreateInputMethodInterface();
	}

	// Select the layout view appropriate for the screen direction, if there is more than one
	@Override public View onCreateInputView() {
		Log.d(TAG, "onCreateInputView;");
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		boolean portrait = mPrefs.getBoolean("ck_portrait_only", false);

		portrait = portrait || (metrics.widthPixels <= metrics.heightPixels);
		Log.v(TAG, "w=" + String.valueOf(metrics.widthPixels) + ", h=" + String.valueOf(metrics.heightPixels) + ", forceP=" + String.valueOf(portrait));

		String err = null;
		ckv = null;
		try {
			XmlPullParser parser = openLayout(getPrefInt(mPrefs, "ck_layout", 0));
			ckv = new CompassKeyboardView(this, portrait, parser);
			ckv.setOnKeyboardActionListener(this);
		}
		catch (XmlPullParserException e)	{ err = e.getMessage(); }
		catch (java.io.IOException e)		{ err = e.getMessage(); }
		if (ckv == null) {
			if (err != null)
				sendNotification("Invalid layout", err);
			err = null;
			try {
				XmlPullParser parser = openLayout(0);
				ckv = new CompassKeyboardView(this, portrait, parser);
			}
			catch (XmlPullParserException e)	{ err = e.getMessage(); }
			catch (java.io.IOException e)		{ err = e.getMessage(); }
			if (err != null)
				sendNotification("Invalid layout", err);
		}

		mPrefs.registerOnSharedPreferenceChangeListener(this);
		Map<String, ?> allPrefs = mPrefs.getAll();
		if (allPrefs != null) {
			Set<String> prefKeys = allPrefs.keySet();
			if (prefKeys != null) {	
				Iterator<String> it = prefKeys.iterator();
				while (it.hasNext()) {
					String key = it.next();
					onSharedPreferenceChanged(mPrefs, key);
				}
			}
		}
		/*ViewParent p = ckv.getParent();
		  if ((p != null) && (p instanceof ViewGroup))
		  ((ViewGroup)p).removeView(ckv); */
		return ckv;
	} 

	@Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
		Log.d(TAG, "onStartInputView;");
		super.onStartInputView(attribute, restarting);
		if (ckv == null)
			return;
		ckv.calculateSizesForMetrics(getResources().getDisplayMetrics());
		ckv.resetState();
		ckv.setInputType(attribute.inputType);
	}

	@Override public void onStartInput(EditorInfo attribute, boolean restarting) {
		super.onStartInput(attribute, restarting); 
		if (ckv == null)
			return;
		ckv.resetState();
		ckv.setInputType(attribute.inputType);
	}

	@Override public boolean onEvaluateFullscreenMode() {
		return false; // never require fullscreen
	}

	private void sendModifiers(InputConnection ic, int action) {
		if (ckv == null)
			return;
		if (ckv.checkState("shift"))
			ic.sendKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_SHIFT_LEFT));
		if (ckv.checkState("alt"))
			ic.sendKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_ALT_LEFT));
		if (ckv.checkState("altgr"))
			ic.sendKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_ALT_RIGHT));
	}

	// Process a generated keycode
	public void onKey(int primaryCode, int[] keyCodes) {
		InputConnection ic = getCurrentInputConnection();
		sendModifiers(ic, KeyEvent.ACTION_DOWN);
		sendDownUpKeyEvents(primaryCode);
		sendModifiers(ic, KeyEvent.ACTION_UP);
	}

	// Process the generated text
	public void onText(CharSequence text) {
		InputConnection ic = getCurrentInputConnection();
		sendModifiers(ic, KeyEvent.ACTION_DOWN);
		sendKeyChar(text.charAt(0));
	} 

	// Process a layout change
	public void setLayout(int n) {
		SharedPreferences.Editor edit = mPrefs.edit();
		edit.putString("ck_layout", String.valueOf(n));
		edit.commit();
		Log.d(TAG, "Set layout, should invalidate view; n='" + String.valueOf(n) + "'");
	}

	// Process a command
	public void execCmd(String cmd) {
		InputConnection ic = getCurrentInputConnection();

		if (cmd.equals("selectStart")) {
			selectionStart = ic.getExtractedText(etreq, 0).selectionStart;
			if ((selectionStart >= 0) && (selectionEnd >= 0)) {
				ic.setSelection(selectionStart, selectionEnd);
				selectionStart = selectionEnd = -1;
			}
		}
		else if (cmd.equals("selectEnd")) {
			selectionEnd = ic.getExtractedText(etreq, 0).selectionEnd;
			if ((selectionStart >= 0) && (selectionEnd >= 0)) {
				ic.setSelection(selectionStart, selectionEnd);
				selectionStart = selectionEnd = -1;
			}
		}
		else if (cmd.equals("selectAll"))
			ic.performContextMenuAction(android.R.id.selectAll);
		else if (cmd.equals("copy"))
			ic.performContextMenuAction(android.R.id.copy);
		else if (cmd.equals("cut"))
			ic.performContextMenuAction(android.R.id.cut);
		else if (cmd.equals("paste"))
			ic.performContextMenuAction(android.R.id.paste);
		else if (cmd.equals("switchIM"))
			ic.performContextMenuAction(android.R.id.switchInputMethod);
		else
			Log.w(TAG, "Unknown cmd '" + cmd + "'");
	}

	public void pickDefaultCandidate() {
	}

	public void swipeRight() {
	}

	public void swipeLeft() {
	}

	// Hide the view
	public void swipeDown() {
		requestHideSelf(0);
	}

	public void swipeUp() {
	}

	public void onPress(int primaryCode) {
	}

	public void onRelease(int primaryCode) {
	} 

	int getPrefInt(SharedPreferences prefs, String key, int def) {
		String s = "";
		try {
			s = prefs.getString(key, "");
			if ((s == null) || s.contentEquals(""))
				return def;
			return Integer.parseInt(s);
		}
		catch (NumberFormatException e) {
			Log.w(TAG, "Invalid value for integer preference; key='" + key + "', value='" + s +"'");
		}
		catch (ClassCastException e) {
			Log.w(TAG, "Found non-string int preference; key='" + key + "', err='" + e.getMessage() + "'");
		}
		return def;

	}

	float getPrefFloat(SharedPreferences prefs, String key, float def) {
		String s = "";
		try {
			s = prefs.getString(key, "");
			if ((s == null) || s.contentEquals(""))
				return def;
			return Float.parseFloat(s);
		}
		catch (NumberFormatException e) {
			Log.w(TAG, "Invalid value for float preference; key='" + key + "', value='" + s +"'");
		}
		catch (ClassCastException e) {
			Log.w(TAG, "Found non-string float preference; key='" + key + "', err='" + e.getMessage() + "'");
		}
		return def;
	}

	// Handle one change in the preferences
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		//Log.d(TAG, "Changing pref "+key);
		{
			Map<String, ?> allPrefs = prefs.getAll();
			if (allPrefs == null) {
				Log.d(TAG, "No prefs found;");
			}
			else {
				Set<String> prefKeys = allPrefs.keySet();
				if (prefKeys != null) {	
					Iterator<String> it = prefKeys.iterator();
					while (it.hasNext()) {
						String k = it.next();
						try {
							String v = prefs.getString(k, "");
							//Log.d(TAG, "Found preference; key='" + k + "', value='" + v + "'");
						}
						catch (ClassCastException e) {
							//Log.d(TAG, "Found non-string preference; key='" + k + "', err='" + e.getMessage() + "'");
						}
					}
				}
			}
		}

		if (key.contentEquals("ck_key_fb_key")) {
			int v = getPrefInt(prefs, "ck_key_fb_key", 0);
			ckv.setVibrateOnKey(v);
		}
		else if (key.contentEquals("ck_key_fb_mod")) {
			int v = getPrefInt(prefs, "ck_key_fb_mod", 0);
			ckv.setVibrateOnModifier(v);
		}
		else if (key.contentEquals("ck_key_fb_cancel")) {
			int v = getPrefInt(prefs, "ck_key_fb_cancel", 0);
			ckv.setVibrateOnCancel(v);
		}
		else if (key.contentEquals("ck_text_fb_normal")) {
			int v = getPrefInt(prefs, "ck_text_fb_normal", 0);
			ckv.setFeedbackNormal(v);
		}
		else if (key.contentEquals("ck_text_fb_password")) {
			int v = getPrefInt(prefs, "ck_text_fb_password", 0);
			ckv.setFeedbackPassword(v);
		}
		else if (key.contentEquals("ck_margin_left")) {
			float f = getPrefFloat(prefs, "ck_margin_left", 0);
			ckv.setLeftMargin(f);
			getWindow().dismiss();
		}
		else if (key.contentEquals("ck_margin_right")) {
			float f = getPrefFloat(prefs, "ck_margin_right", 0);
			ckv.setRightMargin(f);
			getWindow().dismiss();
		}
		else if (key.contentEquals("ck_margin_bottom")) {
			float f = getPrefFloat(prefs, "ck_margin_bottom", 0);
			ckv.setBottomMargin(f);
			getWindow().dismiss();
		}
		else if (key.contentEquals("ck_max_keysize")) {
			float f = getPrefFloat(prefs, "ck_max_keysize", 12);
			ckv.setMaxKeySize(f);
			getWindow().dismiss();
		}
		else if (key.contentEquals("ck_custom_layout")) {
			SharedPreferences.Editor edit = mPrefs.edit();
			String s = prefs.getString("ck_custom_layout", "");
			if (s != "") {
				String err = null;
				try {
					XmlPullParser parser = openLayout(s);
					String name = getLayoutName(parser);

					int n = getPrefInt(prefs, "ck_num_layouts", 3);
					String sn = "["+String.valueOf(n)+"]";

					edit.putString("ck_layout_file"+sn, s);
					edit.putString("ck_layout_name"+sn, name);
					edit.putString("ck_num_layouts", String.valueOf(n + 1));
					edit.putString("ck_layout", String.valueOf(n));
					edit.remove("ck_custom_layout");
					edit.commit();
					Log.d(TAG, "Added custom layout " + String.valueOf(n) + ": " + s);
				}
				catch (FileNotFoundException e)		{ err = e.getMessage(); }
				catch (XmlPullParserException e)	{ err = e.getMessage(); }
				catch (java.io.IOException e)		{ err = e.getMessage(); }

				if (err != null) {
					sendNotification("Invalid layout", err);
				}
			}
		}
		else if (key.contentEquals("ck_layout")) {
			int v = getPrefInt(prefs, "ck_layout", 0);
			if (v != currentLayout) {
				currentLayout = v;
				requestHideSelf(0);
				setInputView(onCreateInputView());
			}
		}
	}
}

// vim: set ai si sw=8 ts=8 noet:
