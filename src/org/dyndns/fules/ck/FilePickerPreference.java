package org.dyndns.fules.ck;
import org.dyndns.fules.ck.R;

import android.app.AlertDialog.Builder;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class FilePickerPreference extends Preference implements PropertyChangeListener, DialogInterface.OnClickListener {
	private static final String	TAG = "FilePickerPreference";
	AlertDialog	dia;
	FilePicker	fp;

	public FilePickerPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialise(context, attrs);
	}

	public FilePickerPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initialise(context, attrs);
	} 

	void initialise(Context context, AttributeSet attrs) {
		fp = new FilePicker(context, attrs);
		fp.setPropertyChangeListener(this);

		AlertDialog.Builder b = new AlertDialog.Builder(context);
		b.setPositiveButton("OK", this);
		b.setNegativeButton("Cancel", this);
		b.setView(fp);
		b.setTitle(fp.getWorkingDir());

		dia = b.create();
	}

	public void propertyChange(PropertyChangeEvent event) {
		String propName = event.getPropertyName();
		if (propName.contentEquals("workingDir")) {
			String s = (String)event.getNewValue();
			dia.setTitle(s);
		}
	}

	@Override protected void onClick() {
		dia.show();
	}

	public void onClick(DialogInterface dialog, int which) {
		if (which == DialogInterface.BUTTON_POSITIVE) {
			//Log.d(TAG, "Clicked Positive");
			String[] files = fp.getSelectedFiles();
			int n = files.length;

			/*for (int i = 0; i < n; i++)
				Log.d(TAG, "file["+String.valueOf(i)+"] = '"+files[i]+"'");*/

			SharedPreferences.Editor ed = getEditor();
			if (n > 0)
				ed.putString(getKey(), files[0]);
			else
				ed.putString(getKey(), "");

			ed.commit();
		}
	}
}
