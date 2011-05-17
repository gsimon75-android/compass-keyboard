package org.dyndns.fules.ck;
import org.dyndns.fules.ck.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class RegexFilter implements FilenameFilter {
	Pattern p;

	RegexFilter(String pattern) {
		p = Pattern.compile(pattern);
	}

	public boolean accept(File dir, String filename) {
		File f = new File(dir, filename);
		return f.isDirectory() || p.matcher(filename).matches();
	}
}

class FileInfoSettings {
	RegexFilter			filter;
	boolean				showHidden, showFiles, showOthers, showUnreadable;

	public FileInfoSettings() {
		filter = null;
		showHidden = true;
		showFiles = true;
		showOthers = true;
		showUnreadable = true;
	}

	public FileInfoSettings(TypedArray a) {
		String s;

		s = a.getString(R.styleable.FilePicker_mask);
		filter = (s != null) ? new RegexFilter(s) : null;

		s = a.getString(R.styleable.FilePicker_showHidden);
		showHidden = (s != null) ? Boolean.parseBoolean(s.toString()) : true;

		s = a.getString(R.styleable.FilePicker_showFiles);
		showFiles = (s != null) ? Boolean.parseBoolean(s.toString()) : true;

		s = a.getString(R.styleable.FilePicker_showOthers);
		showOthers = (s != null) ? Boolean.parseBoolean(s.toString()) : true;

		s = a.getString(R.styleable.FilePicker_showUnreadable);
		showUnreadable = (s != null) ? Boolean.parseBoolean(s.toString()) : true;
	}

}

class FileInfoView extends LinearLayout implements Checkable, View.OnTouchListener {
	private static final String	TAG = "FileInfoView";
	static final int		TYPE_FILE = 0;
	static final int		TYPE_DIR = 1;
	static final int		TYPE_OTHER = 2;
	static final int		TYPE_BROKEN = 3;
	static final int		TYPE_MAX = 4;

	File		file;
	String		dispName;
	int		type;
	boolean		checked, iconClicked;
	TextView	tv;
	ImageView	iv;

	public FileInfoView(Context context, String path, String s) {
		super(context);
		file = new File(path);
		initialise(context, s);
	}
	public FileInfoView(Context context, File f, String s) {
		super(context);
		file = f;
		initialise(context, s);
	}

	void initialise(Context context, String s) {
		setOrientation(android.widget.LinearLayout.HORIZONTAL);

		iv = new ImageView(context);
		addView(iv);

		tv = new TextView(context);
		addView(tv);

		if (!file.canRead()) {
			type = TYPE_BROKEN;
		}
		else if (file.isFile()) {
			type = TYPE_FILE;
		}
		else if (file.isDirectory()) {
			type = TYPE_DIR;
			iv.setOnTouchListener(this);
		}
		else {
			type = TYPE_OTHER;
		}

		dispName = (s == null) ? file.getName() : s;
		tv.setText(dispName);

		checked = true;
		setChecked(false);
	}

	static final Comparator<FileInfoView> DIRS_FIRST = new Comparator<FileInfoView>() {
		public int compare(FileInfoView f1, FileInfoView f2) {
			boolean isDir1 = f1.file.isDirectory();
			boolean isDir2 = f2.file.isDirectory();

			if (isDir1 && !isDir2)
				return -1;
			if (!isDir1 && isDir2)
				return 1;

			return f1.dispName.compareTo(f2.dispName);
		}
	};

        public boolean onTouch(View v, MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN)
			iconClicked = true;
		return false;
	}

	public void setChecked(boolean c) {
		if (c != checked) {
			checked = c;
			if (checked) {
				tv.setTextColor(0xffffff00);
				switch (type) {
					case TYPE_FILE:
						iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_list_active_file));
						break;

					case TYPE_DIR:
						iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_list_active_folder));
						break;

					case TYPE_BROKEN:
						iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_list_active_broken));
						break;
				}
			}
			else {
				tv.setTextColor(0xffffffff);
				switch (type) {
					case TYPE_FILE:
						iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_list_passive_file));
						break;

					case TYPE_DIR:
						iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_list_passive_folder));
						break;

					case TYPE_BROKEN:
						iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_list_passive_broken));
						break;
				}
			}
			iconClicked = false;
		}
	}

	public void toggle() {
		setChecked(!checked);
	}

	public boolean isChecked() {
		return checked;
	}
}

class FileInfoAdapter extends BaseAdapter {
	private static final String	TAG = "FileInfoAdapter";

	int numItems = 0;
	ArrayList<FileInfoView> item = null;

	public FileInfoAdapter(Context context, String baseDir, FileInfoSettings s) {
		super();
		initialise(context, new File(baseDir), s);
	}

	public FileInfoAdapter(Context context, File f, FileInfoSettings s) {
		super();
		initialise(context, f, s);
	}

	void initialise(Context context, File f, FileInfoSettings settings) {
		if (settings == null)
			settings = new FileInfoSettings();

		int numFiles = 0;
		File[] files = null;

		if (f.isDirectory()) {
			files = f.listFiles(settings.filter);
			if (files != null)
				numFiles = files.length;
		}

		item = new ArrayList();
		for (int i = 0; i < numFiles; i++) {
			if (!files[i].canRead() && !settings.showUnreadable)
				continue;

			if (!files[i].isFile() && !settings.showFiles)
				continue;

			if (!files[i].isHidden() && !settings.showHidden)
				continue;

			if (!files[i].isFile() && !files[i].isDirectory() && !settings.showOthers)
				continue;

			item.add(new FileInfoView(context, files[i], null));
		}

		Collections.sort(item, FileInfoView.DIRS_FIRST);

		item.add(0, new FileInfoView(context, "/", "/"));
		File p = f.getParentFile();
		if ((p != null) || (numFiles == 0))
			item.add(1, new FileInfoView(context, p, ".."));
		numItems = item.size();
	}

	@Override public boolean areAllItemsEnabled() {
		return true;
	}

	@Override public int getViewTypeCount() {
		return FileInfoView.TYPE_MAX;
	}

	@Override public boolean hasStableIds() {
		return true;
	}

	@Override public boolean isEmpty() {
		return numItems == 0;
	}

	@Override public boolean isEnabled(int position) {
		return (0 <= position) && (position < numItems);
	}

	@Override public int getCount() {
		return numItems;
	}

	@Override public Object getItem(int position) {
		return item.get(position);
	}

	@Override public long getItemId(int position) {
		return position;
	}

	@Override public int getItemViewType(int position) {
		return item.get(position).type;
	}

	@Override public View getView(int position, View convertView, ViewGroup parent) {
		return item.get(position);
	}
}

public class FilePicker extends ListView implements AdapterView.OnItemClickListener {
	private static final String	TAG = "FilePicker";

	PropertyChangeListener		propertyChangeListener = null;
	FileInfoSettings		settings;
	String				workingDir;

	public FilePicker(Context context) {
		super(context);

		workingDir = "/";
		settings = null;
		initialise(context);
	}

	public FilePicker(Context context, AttributeSet attrs) {
		super(context, attrs);
		CharSequence s;

		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FilePicker);
		s = a.getString(R.styleable.FilePicker_workingDir);
		workingDir = (s != null) ? s.toString() : "/";

		settings = new FileInfoSettings(a);
		a.recycle();

		initialise(context);
	}

	void initialise(Context context) {
		setAdapter(new FileInfoAdapter(context, workingDir, settings));
		setOnItemClickListener(this);
	}

	public void onItemClick(AdapterView parent, View view, int position, long id) {
		FileInfoView fiv = (FileInfoView)getAdapter().getItem(position);

		if ((fiv.type == FileInfoView.TYPE_DIR) && !fiv.iconClicked) {
			String oldWorkingDir = workingDir;
			workingDir = fiv.file.getPath();

			if (propertyChangeListener != null)
				propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "workingDir", oldWorkingDir, workingDir));

			setAdapter(new FileInfoAdapter(parent.getContext(), fiv.file, settings));
		}
	}

	public String getWorkingDir() {
		return workingDir;
	}

	public String[] getSelectedFiles() {
		FileInfoAdapter fia = (FileInfoAdapter)getAdapter();
		int n = fia.getCount();
		int numSelected = 0;

		for (int i = 0; i < n; i++) {
			FileInfoView fiv = (FileInfoView)fia.getItem(i);
			if (fiv.isChecked())
				numSelected++;
		}

		String[] res = new String[numSelected];
		numSelected = 0;
		for (int i = 0; i < n; i++) {
			FileInfoView fiv = (FileInfoView)fia.getItem(i);
			if (fiv.isChecked())
				res[numSelected++] = fiv.file.getPath();
		}

		return res;
	}

	public void setPropertyChangeListener(PropertyChangeListener l) {
		propertyChangeListener = l;
	}
}

