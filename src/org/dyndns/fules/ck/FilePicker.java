package org.dyndns.fules.ck;
import org.dyndns.fules.ck.R;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import java.util.Iterator;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences;

public class FilePicker extends Activity implements FilePickerView.ResultListener {
	private static final String TAG = "FilePicker";

	public static final String EXTRA_PATH = "org.dyndns.fules.ck.filepicker.extra.path";
	public static final String EXTRA_REGEX = "org.dyndns.fules.ck.filepicker.extra.regex";
	public static final String EXTRA_SHOW_HIDDEN = "org.dyndns.fules.ck.filepicker.extra.show.hidden";
	public static final String EXTRA_SHOW_FILES = "org.dyndns.fules.ck.filepicker.extra.show.files";
	public static final String EXTRA_SHOW_OTHERS = "org.dyndns.fules.ck.filepicker.extra.show.others";
	public static final String EXTRA_SHOW_UNREADABLE = "org.dyndns.fules.ck.filepicker.extra.show.unreadable";
	public static final String EXTRA_PREFERENCE = "org.dyndns.fules.ck.filepicker.extra.preference";
	public static final String EXTRA_PREFERENCE_KEY = "org.dyndns.fules.ck.filepicker.extra.preference.key";

    String prefName = null;
    String prefKey = null;

	@Override public void
	onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.filepicker);

		Intent i = getIntent();
		String action = i.getAction();
		if (action.contentEquals(Intent.ACTION_MAIN) || action.contentEquals(Intent.ACTION_PICK)) {
			String s;
			int n;

			FilePickerView fp = (FilePickerView)findViewById(R.id.filepicker);
			fp.setResultListener(this);

			s = i.getStringExtra(EXTRA_PATH);
			if (s != null)
				fp.setWorkingDir(s);

			s = i.getStringExtra(EXTRA_REGEX);
			if (s != null)
				fp.setRegex(s);

			n = i.getIntExtra(EXTRA_SHOW_HIDDEN, -1);
			if (n != -1)
				fp.setShowHidden(n != 0);

			n = i.getIntExtra(EXTRA_SHOW_FILES, -1);
			if (n != -1)
				fp.setShowFiles(n != 0);

			n = i.getIntExtra(EXTRA_SHOW_OTHERS, -1);
			if (n != -1)
				fp.setShowOthers(n != 0);

			n = i.getIntExtra(EXTRA_SHOW_UNREADABLE, -1);
			if (n != -1)
				fp.setShowUnreadable(n != 0);

			prefName = i.getStringExtra(EXTRA_PREFERENCE);
			prefKey = i.getStringExtra(EXTRA_PREFERENCE_KEY);
		}
		else {
			Log.e(TAG, "Unsupported action; value='" + action + "'");
			setResult(Activity.RESULT_CANCELED);
			finish();
		}
	}

	public void onFileSelected(String path, boolean selected) {
		Log.d(TAG, "Selected file; path='" + path + "', state='" + String.valueOf(selected) + "'");
        if ((prefName != null) && (prefKey != null) && (prefName.length() > 0) && (prefKey.length() > 0)) {
        {
                SharedPreferences prefs = getSharedPreferences(prefName, 0);
				SharedPreferences.Editor ed = prefs.edit();
				ed.putString(prefKey, path);
				ed.commit();
			}}
		setResult(Activity.RESULT_OK, new Intent().setAction(Intent.ACTION_PICK).putExtra(EXTRA_PATH, path));
		finish();
	}
}
