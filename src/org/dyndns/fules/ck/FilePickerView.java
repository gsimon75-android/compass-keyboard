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
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dyndns.fules.Symlink;

class RegexFilter implements FilenameFilter {
	Pattern p;
	String s;

	RegexFilter(String pattern) {
		s = pattern;
		p = Pattern.compile(pattern);
	}

	public boolean accept(File dir, String filename) {
		File f = new File(dir, filename);
		return f.isDirectory() || p.matcher(filename).find();
	}

	public String getSource() {
		return s;
	}
}

class FileInfoView extends LinearLayout implements Checkable, View.OnTouchListener {
	private static final String	TAG = "FilePicker";
	static final int		TYPE_FILE = 0;
	static final int		TYPE_DIR = 1;
	static final int		TYPE_OTHER = 2;
	static final int		TYPE_BROKEN = 3;
	static final int		TYPE_MAX = 4;

	File		file;
	String		dispName;
	int			type;
	boolean		checked, iconClicked;
	TextView	tv;
	ImageView	iv;

	public FileInfoView(Context context, File f, String s) {
		super(context);
		file = f;
		setOrientation(android.widget.LinearLayout.HORIZONTAL);

		iv = new ImageView(context);
		addView(iv);

		tv = new TextView(context);
		addView(tv);

        if (Symlink.isLink(file.getPath())) {
            if (s == null)
                s = file.getName();
            file = Symlink.resolveLink(file);
        }

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

		if (s == null)
			dispName = file.getName();
		else
			dispName = s;
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
				if (type == TYPE_FILE)
					iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_list_active_file));
				else if (type == TYPE_DIR)
					iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_list_active_folder));
				else if (type == TYPE_BROKEN)
					iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_list_active_broken));
			}
			else {
				tv.setTextColor(0xffffffff);
				if (type == TYPE_FILE)
					iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_list_passive_file));
				else if (type == TYPE_DIR)
					iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_list_passive_folder));
				else if (type == TYPE_BROKEN)
					iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_list_passive_broken));
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


public class FilePickerView extends ListView implements AdapterView.OnItemClickListener {
	private static final String	TAG = "FilePicker";

	interface ResultListener {
		void onFileSelected(String path, boolean selected);
	}

	RegexFilter			filter = null;
	boolean				showHidden = true;
	boolean				showFiles = true;
	boolean				showOthers = true;
	boolean				showUnreadable = true;
	String				workingDir = "/";
	ResultListener		listener = null;

	class FileInfoAdapter extends BaseAdapter {
		int numItems = 0;
		ArrayList<FileInfoView> item = null;
		File base = null;

		public FileInfoAdapter(File f) {
			super();
			base = Symlink.resolveLink(f);
			refresh();
		}

		public void refresh() {
			int numFiles = 0;
			File[] files = null;

			item = null;
			numItems = 0;
			if (base.isDirectory()) {
				files = base.listFiles(filter);
				if (files != null)
					numFiles = files.length;
			}

			item = new ArrayList();
			for (int i = 0; i < numFiles; i++) {
                files[i] = Symlink.resolveLink(files[i]);
				if (!files[i].canRead() && !showUnreadable)
					continue;

				if (files[i].isFile() && !showFiles)
					continue;

				if (files[i].isHidden() && !showHidden)
					continue;

				if (!files[i].isFile() && !files[i].isDirectory() && !showOthers)
					continue;

				item.add(new FileInfoView(getContext(), files[i], null));
			}

			Collections.sort(item, FileInfoView.DIRS_FIRST);

			item.add(0, new FileInfoView(getContext(), new File("/"), "/"));
			File p = base.getParentFile();
			if ((p != null) || (numFiles == 0))
				item.add(1, new FileInfoView(getContext(), p, ".."));
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

	public FilePickerView(Context context) {
		super(context);
		setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		setAdapter(new FileInfoAdapter(new File(workingDir)));
		setOnItemClickListener(this);
	}

	public FilePickerView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		setAdapter(new FileInfoAdapter(new File(workingDir)));
		setOnItemClickListener(this);
	}

	void setResultListener(ResultListener l) {
		listener = l;
	}

	public void onItemClick(AdapterView parent, View view, int position, long id) {
		FileInfoView fiv = (FileInfoView)getAdapter().getItem(position);

		if ((fiv.type == FileInfoView.TYPE_DIR) && !fiv.iconClicked) {
			workingDir = fiv.file.getPath();
			setAdapter(new FileInfoAdapter(fiv.file));
		}
		else {
			fiv.toggle();
			if (listener != null)
				listener.onFileSelected(fiv.file.getPath(), fiv.isChecked());
		}
	}

	void refresh() {
		((FileInfoAdapter)getAdapter()).refresh();
		invalidate(); // postInvalidate();
	}

	public String getWorkingDir() {
		return workingDir;
	}

	public void setWorkingDir(String wd) {
		workingDir = wd;
		setAdapter(new FileInfoAdapter(new File(workingDir)));
	}

	void setRegex(String s) {
        if (s != null)
            Log.d(TAG, "Setting regex; src='" + s + "'");
        else
            Log.d(TAG, "Clearing regex;");
		filter = ((s != null) && (s.length() > 0)) ? new RegexFilter(s) : null;
	}

	void setShowHidden(boolean v) {
		if (showHidden != v) {
			showHidden = v;
			refresh();
		}
	}

	void setShowFiles(boolean v) {
		if (showFiles != v) {
			showFiles = v;
			refresh();
		}
	}

	void setShowOthers(boolean v) {
		if (showOthers != v) {
			showOthers = v;
			refresh();
		}
	}

	void setShowUnreadable(boolean v) {
		if (showUnreadable != v) {
			showUnreadable = v;
			refresh();
		}
	}
}
