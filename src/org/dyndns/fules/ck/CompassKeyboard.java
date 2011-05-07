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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Iterator;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class CompassKeyboard extends InputMethodService implements KeyboardView.OnKeyboardActionListener, SharedPreferences.OnSharedPreferenceChangeListener  {
	public static final String	SHARED_PREFS_NAME = "CompassKeyboardSettings";
	public static final int[]	builtinLayouts = { R.xml.default_latin, R.xml.default_cyrillic, R.xml.default_greek }; // keep in sync with constants.xml
	private static final String	TAG = "CompassKeyboard";

	private SharedPreferences	mPrefs;					// the preferences instance
	CompassKeyboardView		ckvHorizontal, ckvVertical;		// the layout views for horizontal and vertical screens
	CompassKeyboardView		ckv;					// the current layout view, either @ckvHorizontal or @ckvVertical
	int				currentBuiltinLayout = 0;
	String				currentLayoutFile;

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
	void updateLayoutFromParser(XmlPullParser parser) throws XmlPullParserException, java.io.IOException {
		while (parser.getEventType() == XmlPullParser.START_DOCUMENT)
			parser.next();

		if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("CompassKeyboard"))
			throw new XmlPullParserException("Expected <CompassKeyboard>", parser, null);
		parser.nextTag();

		while (parser.getEventType() != XmlPullParser.END_TAG) {
			if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("Layout"))
				throw new XmlPullParserException("Expected <Layout>", parser, null);
			String name = parser.getAttributeValue(null, "name");
			//Log.d(TAG, "Reading layout '"+name+"'");

			if (name.contentEquals("horizontal"))
				ckvHorizontal.readLayout(parser);
			else if (name.contentEquals("vertical"))
				ckvVertical.readLayout(parser);
			else
				throw new XmlPullParserException("Invalid Layout name '"+name+"'", parser, null);
		}

		if (!parser.getName().contentEquals("CompassKeyboard"))
			throw new XmlPullParserException("Expected </CompassKeyboard>", parser, null);
		parser.next();
	}

	// Update the layout if needed
	boolean updateLayout(int i, String fname) {
		String err = null;

		// a custom layout without a file selected means reverting to the 1st built-in
		if ((i < 0) && ((fname == null) || fname.contentEquals("")))
			i = 0;

		// a custom layout with too big index means reverting to the 1st built-in
		if ((i >= 0) && (i >= builtinLayouts.length)) {
			sendNotification("Invalid index", String.valueOf(i)+", reverting to 0");
			i = 0;
		}

		try {
			if (i < 0) {
				// don't reload the already loaded file
				if ((i == currentBuiltinLayout) && (currentLayoutFile != null) && currentLayoutFile.contentEquals(fname))
					return true;

				Log.d(TAG, "Loading custom layout '"+fname+"'");
				FileInputStream is = new FileInputStream(fname);
				XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
				factory.setNamespaceAware(false);
				XmlPullParser parser = factory.newPullParser();
				parser.setInput(is, null);
				updateLayoutFromParser(parser);
			}
			else {
				// don't reload the already loaded built-in layout
				if (i == currentBuiltinLayout)
					return true;

				Log.d(TAG, "Loading built-in layout '"+String.valueOf(i)+"'");
				updateLayoutFromParser(getResources().getXml(builtinLayouts[i]));
			}
		}
		catch (FileNotFoundException e)		{ err = e.getMessage(); }
		catch (SecurityException e)		{ err = e.getMessage(); }
		catch (XmlPullParserException e)	{ err = e.getMessage(); }
		catch (java.io.IOException e)		{ err = e.getMessage(); }

		if (err == null) {
			currentBuiltinLayout = i;
			currentLayoutFile = fname;
			return true;
		}

		if (i < 0)
			sendNotification("Invalid layout file '"+fname+"'", err);
		else
			sendNotification("Invalid built-in layout "+String.valueOf(i), err);

		// no layout this far, so use the 1st built-in, unless we have just tried that one
		if (i == 0)
			return false;
		currentBuiltinLayout = -1;
		return updateLayout(0, null);
	}

	@Override public void onInitializeInterface() {
		mPrefs = getSharedPreferences(SHARED_PREFS_NAME, 0);

		ckvHorizontal = new CompassKeyboardView(this);
		ckvHorizontal.setOnKeyboardActionListener(this);

		ckvVertical = new CompassKeyboardView(this);
		ckvVertical.setOnKeyboardActionListener(this);

		ckv = ckvVertical;

		int i = currentBuiltinLayout;
		currentBuiltinLayout--; // enforce reload
		updateLayout(i, currentLayoutFile);

		mPrefs.registerOnSharedPreferenceChangeListener(this);
		onSharedPreferenceChanged(mPrefs, "ck_vibr_key");
		onSharedPreferenceChanged(mPrefs, "ck_vibr_mod");
		onSharedPreferenceChanged(mPrefs, "ck_vibr_cancel");
		onSharedPreferenceChanged(mPrefs, "ck_builtin_layout");
	}

	// Select the layout view appropriate for the screen direction, if there is more than one
	@Override public View onCreateInputView() {
		DisplayMetrics metrics = getResources().getDisplayMetrics();

		if (metrics.widthPixels > metrics.heightPixels) {
			if (ckvHorizontal != null)
				ckv = ckvHorizontal;
			else
				ckv = ckvVertical;
		}
		else {
			if (ckvVertical != null)
				ckv = ckvVertical;
			else
				ckv = ckvHorizontal;
		}

		if (ckv != null)
			ckv.calculateSizesForMetrics(metrics);
		else
			Log.e(TAG, "onCreateInputView: ckv is null");
		return ckv;
	} 

	@Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
		super.onStartInputView(attribute, restarting);
		if (ckv != null) {
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

	// Process a generated keycode
	public void onKey(int primaryCode, int[] keyCodes) {
		getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, primaryCode));
		getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, primaryCode));
	}

	// Process the generated text
	public void onText(CharSequence text) {
		getCurrentInputConnection().commitText(text, 1);
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
		String s = prefs.getString(key, "");

		if ((s == null) || s.contentEquals(""))
			return def;
		return Integer.parseInt(s);

	}

	// Handle one change in the preferences
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		//Log.d(TAG, "Changing pref "+key);

		if (key.contentEquals("ck_vibr_key")) {
			int v = getPrefInt(prefs, key, 0);
			ckvHorizontal.setVibrateOnKey(v);
			ckvVertical.setVibrateOnKey(v);
		}
		else if (key.contentEquals("ck_vibr_mod")) {
			int v = getPrefInt(prefs, key, 0);
			ckvHorizontal.setVibrateOnModifier(v);
			ckvVertical.setVibrateOnModifier(v);
		}
		else if (key.contentEquals("ck_vibr_cancel")) {
			int v = getPrefInt(prefs, key, 0);
			ckvHorizontal.setVibrateOnCancel(v);
			ckvVertical.setVibrateOnCancel(v);
		}
		else if (key.contentEquals("ck_feedback_normal")) {
			int v = getPrefInt(prefs, key, 0);
			ckvHorizontal.setFeedbackNormal(v);
			ckvVertical.setFeedbackNormal(v);
		}
		else if (key.contentEquals("ck_feedback_password")) {
			int v = getPrefInt(prefs, key, 0);
			ckvHorizontal.setFeedbackPassword(v);
			ckvVertical.setFeedbackPassword(v);
		}
		else if (key.contentEquals("ck_builtin_layout") ||
			 key.contentEquals("ck_layoutfile")) {
			int i = getPrefInt(prefs, "ck_builtin_layout", -2);
			String filename = prefs.getString("ck_layoutfile", "");
			Log.d(TAG, "Layout settings changed; l='"+String.valueOf(i)+"', file='"+filename+"'");

			if (i >= 0) {
				// built-in layout requested
				updateLayout(i, null);
			}
			else if (i == -1) {
				// custom layout requested
				if ((filename != null) && !filename.contentEquals(""))
					updateLayout(-1, filename);
				else
					updateLayout(0, null);
			}
			else {
				// no layout selected at all -> revert to the 1st built-in
				updateLayout(0, null);
			}
		}
	}
}
