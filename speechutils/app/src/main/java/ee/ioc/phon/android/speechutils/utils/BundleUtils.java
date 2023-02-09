package ee.ioc.phon.android.speechutils.utils;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BundleUtils {

    private BundleUtils() {
    }

    public static List<String> ppBundle(Bundle bundle) {
        return ppBundle("/", bundle);
    }


    private static List<String> ppBundle(String bundleName, Bundle bundle) {
        List<String> strings = new ArrayList<>();
        if (bundle == null) {
            return strings;
        }
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            String name = bundleName + key;
            if (value instanceof Bundle) {
                strings.addAll(ppBundle(name + "/", (Bundle) value));
            } else {
                if (value instanceof Object[]) {
                    strings.add(name + ": " + Arrays.toString((Object[]) value));
                } else if (value instanceof float[]) {
                    strings.add(name + ": " + Arrays.toString((float[]) value));
                } else {
                    strings.add(name + ": " + value);
                }
            }
        }
        return strings;
    }


    /**
     * <p>Traverses the given bundle looking for the given key. The search also
     * looks into embedded bundles and thus differs from {@code Bundle.get(String)}.
     * Returns the first found entry as an object. If the given bundle does not
     * contain the given key then returns {@code null}.</p>
     *
     * @param bundle bundle (e.g. intent extras)
     * @param key    key of a bundle entry (possibly in an embedded bundle)
     * @return first matching key's value
     */
    public static Object getBundleValue(Bundle bundle, String key) {
        if (bundle == null) {
            return null;
        }
        for (String k : bundle.keySet()) {
            Object value = bundle.get(k);
            if (value instanceof Bundle) {
                Object deepValue = getBundleValue((Bundle) value, key);
                if (deepValue != null) {
                    return deepValue;
                }
            } else if (key.equals(k)) {
                return value;
            }
        }
        return null;
    }

    /**
     * @param bundle bundle that is assumed to contain just strings
     * @return map of strings generated from the given bundle
     */
    public static Map<String, String> getBundleAsMapOfString(Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        Map<String, String> map = new HashMap<>();
        for (String key : bundle.keySet()) {
            map.put(key, bundle.getString(key));
        }
        return map;
    }

    public static Bundle createResultsBundle(String hypothesis) {
        ArrayList<String> hypotheses = new ArrayList<>();
        hypotheses.add(hypothesis);
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION, hypotheses);
        return bundle;
    }
}