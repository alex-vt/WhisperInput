package ee.ioc.phon.android.speechutils.utils;

import android.content.SharedPreferences;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PreferenceUtils {

    private static final String SEP = "/";

    private PreferenceUtils() {
    }

    public static String getPrefString(SharedPreferences prefs, String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }

    public static String getPrefString(SharedPreferences prefs, Resources res, int key, int defaultValue) {
        return getPrefString(prefs, res.getString(key), res.getString(defaultValue));
    }

    public static String getPrefString(SharedPreferences prefs, Resources res, int key) {
        return prefs.getString(res.getString(key), null);
    }

    public static Set<String> getPrefStringSet(SharedPreferences prefs, Resources res, int key) {
        try {
            return prefs.getStringSet(res.getString(key), Collections.<String>emptySet());
        } catch (ClassCastException e) {
            return Collections.emptySet();
        }
    }

    public static Set<String> getPrefStringSet(SharedPreferences prefs, Resources res, int key, int defaultValue) {
        return prefs.getStringSet(res.getString(key), getStringSetFromStringArray(res, defaultValue));
    }

    public static boolean getPrefBoolean(SharedPreferences prefs, Resources res, int key, int defaultValue) {
        try {
            return prefs.getBoolean(res.getString(key), res.getBoolean(defaultValue));
        } catch (ClassCastException e) {
            // This can happen if the key is reused for a different purpose and the value has now a different type
            // than stored (by an earlier version of the app).
            return false;
        }
    }

    public static int getPrefInt(SharedPreferences prefs, Resources res, int key, int defaultValue) {
        return Integer.parseInt(getPrefString(prefs, res, key, defaultValue));
    }

    public static String getUniqueId(SharedPreferences settings) {
        String id = settings.getString("id", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            SharedPreferences.Editor editor = settings.edit();
            editor.putString("id", id);
            editor.apply();
        }
        return id;
    }

    public static Set<String> getStringSetFromStringArray(Resources res, int key) {
        return new HashSet<>(Arrays.asList(res.getStringArray(key)));
    }

    public static List<String> getStringListFromStringArray(Resources res, int key) {
        return Arrays.asList(res.getStringArray(key));
    }

    public static void putPrefString(SharedPreferences prefs, String key, String value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.apply();
    }

    public static void putPrefBoolean(SharedPreferences prefs, Resources res, int key, boolean value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(res.getString(key), value);
        editor.apply();
    }

    public static void putPrefString(SharedPreferences prefs, Resources res, int key, String value) {
        putPrefString(prefs, res.getString(key), value);
    }

    public static void putPrefStringSet(SharedPreferences prefs, Resources res, int key, Set<String> value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(res.getString(key), value);
        editor.apply();
    }

    public static boolean togglePrefStringSetEntry(SharedPreferences prefs, Resources res, int key, String value) {
        Set<String> set;
        try {
            set = prefs.getStringSet(res.getString(key), new HashSet<>());
        } catch (ClassCastException e) {
            set = new HashSet<>();
        }
        boolean b = set.contains(value);
        if (b) {
            set.remove(value);
        } else {
            set.add(value);
        }
        putPrefStringSet(prefs, res, key, set);
        return !b;
    }

    /**
     * Stores the given key-value pair into a map with the given name.
     * If value is null, then delete the entry from the preferences.
     */
    public static void putPrefMapEntry(SharedPreferences prefs, Resources res, int nameId, String key, String value) {
        String name = res.getString(nameId);
        Set<String> keys = prefs.getStringSet(name, new HashSet<>());
        SharedPreferences.Editor editor = prefs.edit();
        String nameKey = name + SEP + key;
        if (value == null) {
            editor.remove(nameKey);
            if (keys.contains(key)) {
                keys.remove(key);
                editor.putStringSet(name, keys);
            }
        } else {
            editor.putString(nameKey, value);
            if (!keys.contains(key)) {
                keys.add(key);
                editor.putStringSet(name, keys);
            }
        }
        editor.apply();
    }

    /**
     * Stores the given key-value pair into a map with the given name.
     * If value is null, then delete the entry from the preferences.
     */
    public static void putPrefMapEntry(SharedPreferences prefs, Resources res, int nameId, String key, Integer value) {
        String name = res.getString(nameId);
        Set<String> keys = prefs.getStringSet(name, new HashSet<>());
        SharedPreferences.Editor editor = prefs.edit();
        String nameKey = name + SEP + key;
        if (value == null) {
            editor.remove(nameKey);
            if (keys.contains(key)) {
                keys.remove(key);
                editor.putStringSet(name, keys);
            }
        } else {
            editor.putInt(nameKey, value);
            if (!keys.contains(key)) {
                keys.add(key);
                editor.putStringSet(name, keys);
            }
        }
        editor.apply();
    }

    public static String getPrefMapEntry(SharedPreferences prefs, Resources res, int nameId, String key) {
        return prefs.getString(res.getString(nameId) + SEP + key, null);
    }

    public static int getPrefMapEntryInt(SharedPreferences prefs, Resources res, int nameId, String key, int defValue) {
        return prefs.getInt(res.getString(nameId) + SEP + key, defValue);
    }

    public static Set<String> getPrefMapKeys(SharedPreferences prefs, Resources res, int nameId) {
        return prefs.getStringSet(res.getString(nameId), Collections.<String>emptySet());
    }

    @NonNull
    public static Map<String, String> getPrefMap(SharedPreferences prefs, Resources res, int nameId) {
        String name = res.getString(nameId);
        Set<String> keys = prefs.getStringSet(name, Collections.<String>emptySet());
        Map<String, String> map = new HashMap<>();
        for (String key : keys) {
            map.put(key, prefs.getString(name + SEP + key, null));
        }
        return map;
    }

    public static void clearPrefMap(SharedPreferences prefs, Resources res, int nameId) {
        String name = res.getString(nameId);
        Set<String> keys = prefs.getStringSet(name, null);
        if (keys != null) {
            SharedPreferences.Editor editor = prefs.edit();
            for (String key : keys) {
                editor.remove(name + SEP + key);
            }
            editor.remove(name);
            editor.apply();
        }
    }

    public static void clearPrefMap(SharedPreferences prefs, Resources res, int nameId, Set<String> deleteKeys) {
        String name = res.getString(nameId);
        Set<String> keys = prefs.getStringSet(name, null);
        if (keys != null) {
            SharedPreferences.Editor editor = prefs.edit();
            for (String key : deleteKeys) {
                editor.remove(name + SEP + key);
            }
            Set<String> newKeys = new HashSet<>(keys);
            newKeys.removeAll(deleteKeys);
            editor.putStringSet(name, newKeys);
            editor.apply();
        }
    }
}