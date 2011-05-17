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
import java.util.ArrayList;
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
	int				currentLayout;

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
	String updateLayoutFromParser(XmlPullParser parser) throws XmlPullParserException, java.io.IOException {
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
					name = updateLayoutFromParser(getResources().getXml(R.xml.default_latin));
					break;

				case 1:
					name = updateLayoutFromParser(getResources().getXml(R.xml.default_cyrillic));
					break;

				case 2:
					name = updateLayoutFromParser(getResources().getXml(R.xml.default_greek));
					break;

				default:
					String s = mPrefs.getString("ck_layout_file["+String.valueOf(i)+"]", "");
					if ((s == null) || s.contentEquals(""))
						throw new FileNotFoundException("Invalid Layout index '"+String.valueOf(i)+"'");

					FileInputStream is = new FileInputStream(s);
					XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
					factory.setNamespaceAware(false);
					XmlPullParser parser = factory.newPullParser();
					parser.setInput(is, null);
					name = updateLayoutFromParser(parser);
					break;
			}
		}
		catch (FileNotFoundException e)		{ err = e.getMessage(); }
		catch (SecurityException e)		{ err = e.getMessage(); }
		catch (XmlPullParserException e)	{ err = e.getMessage(); }
		catch (java.io.IOException e)		{ err = e.getMessage(); }

		if (err == null) {
			currentLayout = i;
			return name;
		}

		sendNotification("Invalid layout '"+String.valueOf(i)+"'", err);
		// revert to default latin, unless this was the one that has failed
		if (i == 0)
			return null;
		currentLayout = -1;
		return updateLayout(0);
	}

	@Override public void onInitializeInterface() {
		mPrefs = getSharedPreferences(SHARED_PREFS_NAME, 0);

		ckvHorizontal = new CompassKeyboardView(this);
		ckvHorizontal.setOnKeyboardActionListener(this);

		ckvVertical = new CompassKeyboardView(this);
		ckvVertical.setOnKeyboardActionListener(this);

		ckv = ckvVertical;

		int i = currentLayout;
		currentLayout = -1;			// enforce reloading layout
		updateLayout(i);

		mPrefs.registerOnSharedPreferenceChangeListener(this);
		onSharedPreferenceChanged(mPrefs, "ck_vibr_key");
		onSharedPreferenceChanged(mPrefs, "ck_vibr_mod");
		onSharedPreferenceChanged(mPrefs, "ck_vibr_cancel");
		onSharedPreferenceChanged(mPrefs, "ck_layout");
		onSharedPreferenceChanged(mPrefs, "ck_feedback_normal");
		onSharedPreferenceChanged(mPrefs, "ck_feedback_password");
	}

	// Select the layout view appropriate for the screen direction, if there is more than one
	@Override public View onCreateInputView() {
		DisplayMetrics metrics = getResources().getDisplayMetrics();

		if (metrics.widthPixels > metrics.heightPixels)
			ckv = (ckvHorizontal != null) ? ckvHorizontal : ckvVertical;
		else
			ckv = (ckvVertical != null) ? ckvVertical : ckvHorizontal;

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
		//Log.d(TAG, "Changing pref "+key+" to "+prefs.getString(key, ""));

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
		else if (key.contentEquals("ck_layout")) {
			int i = getPrefInt(prefs, key, 0);
			updateLayout(i);
		}
		else if (key.contentEquals("ck_custom_layout")) {
			SharedPreferences.Editor edit = mPrefs.edit();
			String s = prefs.getString(key, "");

			int n = getPrefInt(prefs, "ck_num_layouts", 3);
			String sn = "["+String.valueOf(n)+"]";

			edit.putString("ck_layout_file"+sn, s);
			edit.putString("ck_num_layouts", String.valueOf(n + 1));
			edit.commit();

			String name = updateLayout(n);

			edit.putString("ck_layout_name"+sn, name);
			edit.commit();
		}
	}
}
