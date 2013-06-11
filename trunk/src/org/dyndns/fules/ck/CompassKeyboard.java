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
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.Map;
import java.util.Set;
import java.util.Iterator;

public class CompassKeyboard extends InputMethodService implements KeyboardView.OnKeyboardActionListener, SharedPreferences.OnSharedPreferenceChangeListener  {
	public static final String	SHARED_PREFS_NAME = "CompassKeyboardSettings";
	public static final int[]	builtinLayouts = { R.xml.default_latin, R.xml.default_cyrillic, R.xml.default_greek }; // keep in sync with constants.xml
	private static final String	TAG = "CompassKeyboard";

	private SharedPreferences	mPrefs;					// the preferences instance
	CompassKeyboardView		ckvHorizontal, ckvVertical;		// the layout views for horizontal and vertical screens
	CompassKeyboardView		ckv;					// the current layout view, either @ckvHorizontal or @ckvVertical
	int				currentLayout;
	boolean				forcePortrait;				// use the portrait layout even for horizontal screens

	ExtractedTextRequest		etreq = new ExtractedTextRequest();
	int				selectionStart = -1, selectionEnd = -1;

	// send an auto-revoked notification with a title and a message
	void sendNotification(String title, String msg) {
		// Simple as a pie, isn't it...
		Notification n = new Notification(android.R.drawable.ic_notification_clear_all, title, System.currentTimeMillis());
		n.flags = Notification.FLAG_AUTO_CANCEL;
		n.setLatestEventInfo(this, title, msg, PendingIntent.getActivity(this, 0, new Intent(), 0));
		((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, n);
		Log.e(TAG, title+"; "+msg);
	}

	// Read a layout from a parser, both horizontal and vertical, if possible
	String updateLayout(XmlPullParser parser) throws XmlPullParserException, java.io.IOException {
		String name;

		while (parser.getEventType() == XmlPullParser.START_DOCUMENT)
			parser.next();

		if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("CompassKeyboard"))
			throw new XmlPullParserException("Expected <CompassKeyboard>", parser, null);

		name = parser.getAttributeValue(null, "name");
		if (name != null)
			Log.i(TAG, "Loading keyboard '"+name+"'");
		parser.nextTag();

		while (parser.getEventType() != XmlPullParser.END_TAG) {
			if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("Layout"))
				throw new XmlPullParserException("Expected <Layout>", parser, null);
			String layoutName = parser.getAttributeValue(null, "name");

			if (layoutName.contentEquals("horizontal"))
				ckvHorizontal.readLayout(parser);
			else if (layoutName.contentEquals("vertical"))
				ckvVertical.readLayout(parser);
			else
				throw new XmlPullParserException("Invalid Layout name '"+layoutName+"'", parser, null);
		}

		if (!parser.getName().contentEquals("CompassKeyboard"))
			throw new XmlPullParserException("Expected </CompassKeyboard>", parser, null);
		parser.next();

		return name;
	}

	public String updateLayout(String filename) throws XmlPullParserException, java.io.IOException {
		FileInputStream is = new FileInputStream(filename);
		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
		factory.setNamespaceAware(false);
		XmlPullParser parser = factory.newPullParser();
		parser.setInput(is, null);
		return updateLayout(parser);
	}

	// Update the layout if needed
	public String updateLayout(int i) {
		String name = null;
		String err = null;

		if ((i < 0) || (i == currentLayout))
			return "same";

		Log.d(TAG, "Loading layout '"+String.valueOf(i)+"'");
		try {
			switch (i) {
				case 0:
					name = updateLayout(getResources().getXml(R.xml.default_latin));
					break;

				case 1:
					name = updateLayout(getResources().getXml(R.xml.default_cyrillic));
					break;

				case 2:
					name = updateLayout(getResources().getXml(R.xml.default_greek));
					break;

				default:
					String s = mPrefs.getString("ck_layout_file["+String.valueOf(i)+"]", "");
					if ((s == null) || s.contentEquals(""))
						throw new FileNotFoundException("Invalid Layout index '"+String.valueOf(i)+"'");
					name = updateLayout(s);
					break;
			}
		}
		catch (FileNotFoundException e)		{ err = e.getMessage(); }
		catch (XmlPullParserException e)	{ err = e.getMessage(); }
		catch (java.io.IOException e)		{ err = e.getMessage(); }

		if (err == null) {
			currentLayout = i;
			return name;
		}

		sendNotification("Invalid layout", err);
		// revert to default latin, unless this was the one that has failed
		if (i != 0) {
			currentLayout = -1;
			updateLayout(0);
		}
		return null;
	}

	@Override public AbstractInputMethodService.AbstractInputMethodImpl onCreateInputMethodInterface() {
		Log.d(TAG, "onCreateInputMethodInterface;");
		mPrefs = getSharedPreferences(SHARED_PREFS_NAME, 0);
		etreq.hintMaxChars = etreq.hintMaxLines = 0;

		ckvHorizontal = new CompassKeyboardView(this);
		ckvHorizontal.setOnKeyboardActionListener(this);

		ckvVertical = new CompassKeyboardView(this);
		ckvVertical.setOnKeyboardActionListener(this);

		ckv = ckvVertical;

		int i = currentLayout;
		currentLayout = -1;			// enforce reloading layout
		updateLayout(i);

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
		return super.onCreateInputMethodInterface();
	}

	// Select the layout view appropriate for the screen direction, if there is more than one
	@Override public View onCreateInputView() {
		//Log.d(TAG, "onCreateInputView;");
		DisplayMetrics metrics = getResources().getDisplayMetrics();

		if (forcePortrait || (metrics.widthPixels <= metrics.heightPixels))
			ckv = (ckvVertical != null) ? ckvVertical : ckvHorizontal;
		else
			ckv = (ckvHorizontal != null) ? ckvHorizontal : ckvVertical;

		Log.v(TAG, "w=" + String.valueOf(metrics.widthPixels) + ", h=" + String.valueOf(metrics.heightPixels) + ", forceP=" + String.valueOf(forcePortrait));
		/*if (ckv != null)
			ckv.calculateSizesForMetrics(metrics); */
	
		ViewParent p = ckv.getParent();
		if ((p != null) && (p instanceof ViewGroup))
			((ViewGroup)p).removeView(ckv);
		return ckv;
	} 

	@Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
		//Log.d(TAG, "onStartInputView;");
		super.onStartInputView(attribute, restarting);
		if (ckv != null) {
			ckv.calculateSizesForMetrics(getResources().getDisplayMetrics());
			ckv.resetState();
			ckv.setInputType(attribute.inputType);
		}
	}

	@Override public void onStartInput(EditorInfo attribute, boolean restarting) {
		super.onStartInput(attribute, restarting); 
		if (ckv != null) {
			ckv.resetState();
			ckv.setInputType(attribute.inputType);
		}
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
			int v = getPrefInt(prefs, key, 0);
			ckvHorizontal.setVibrateOnKey(v);
			ckvVertical.setVibrateOnKey(v);
		}
		else if (key.contentEquals("ck_key_fb_mod")) {
			int v = getPrefInt(prefs, key, 0);
			ckvHorizontal.setVibrateOnModifier(v);
			ckvVertical.setVibrateOnModifier(v);
		}
		else if (key.contentEquals("ck_key_fb_cancel")) {
			int v = getPrefInt(prefs, key, 0);
			ckvHorizontal.setVibrateOnCancel(v);
			ckvVertical.setVibrateOnCancel(v);
		}
		else if (key.contentEquals("ck_text_fb_normal")) {
			int v = getPrefInt(prefs, key, 0);
			ckvHorizontal.setFeedbackNormal(v);
			ckvVertical.setFeedbackNormal(v);
		}
		else if (key.contentEquals("ck_text_fb_password")) {
			int v = getPrefInt(prefs, key, 0);
			ckvHorizontal.setFeedbackPassword(v);
			ckvVertical.setFeedbackPassword(v);
		}
		else if (key.contentEquals("ck_margin_left")) {
			float f = getPrefFloat(prefs, key, 0);
			ckvHorizontal.setLeftMargin(f);
			ckvVertical.setLeftMargin(f);
			getWindow().dismiss();
		}
		else if (key.contentEquals("ck_margin_right")) {
			float f = getPrefFloat(prefs, key, 0);
			ckvHorizontal.setRightMargin(f);
			ckvVertical.setRightMargin(f);
			getWindow().dismiss();
		}
		else if (key.contentEquals("ck_margin_bottom")) {
			float f = getPrefFloat(prefs, key, 0);
			ckvHorizontal.setBottomMargin(f);
			ckvVertical.setBottomMargin(f);
			getWindow().dismiss();
		}
		else if (key.contentEquals("ck_max_keysize")) {
			float f = getPrefFloat(prefs, key, 12);
			ckvHorizontal.setMaxKeySize(f);
			ckvVertical.setMaxKeySize(f);
			getWindow().dismiss();
		}
		else if (key.contentEquals("ck_layout")) {
			int i = getPrefInt(prefs, key, 0);
			updateLayout(i);
		}
		else if (key.contentEquals("ck_custom_layout")) {
			SharedPreferences.Editor edit = mPrefs.edit();
			String s = prefs.getString(key, "");
			if (s != "") {
				String err = null;
				try {
					String name = updateLayout(s);

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
		else if (key.contentEquals("ck_portrait_only")) {
			forcePortrait = prefs.getBoolean(key, false);
		}
	}
}

// vim: set ai si sw=8 ts=8 noet:
