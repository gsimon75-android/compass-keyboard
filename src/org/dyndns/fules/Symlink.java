package org.dyndns.fules;

import android.util.Log;
import java.util.HashSet;
import java.io.File;

public class Symlink {
    static {
        System.loadLibrary("symlink");
    }
    public static final String TAG = "Symlink";
    public static native int create(String from, String to);
    public static native boolean isLink(String name);
    public static native String readLink(String name);

    public static String resolveLink(String name) {
        HashSet<String> links = new HashSet<String>();
        while (Symlink.isLink(name)) {
            if (!links.add(name)) {
                // circular symlink, TODO: how to handle?
                Log.e(TAG, "Circular symlink found; name='" + name + "'");
                return name;
            }
            name = readLink(name);
        }
        return name;
    }

    public static File resolveLink(File f) {
        String name = f.getPath();
        if (!isLink(name))
            return f;
        return new File(resolveLink(name));
    }
}

