package ee.ioc.phon.android.speechutils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TtsLocaleMapper {

    private static final List<Locale> SIMILAR_LOCALES_ET;

    static {
        List<Locale> aListEt = new ArrayList<>();
        SIMILAR_LOCALES_ET = Collections.unmodifiableList(aListEt);
    }

    private static final Map<Locale, List<Locale>> SIMILAR_LOCALES;

    static {
        Map<Locale, List<Locale>> aMap = new HashMap<>();
        SIMILAR_LOCALES = Collections.unmodifiableMap(aMap);
    }

    public static List<Locale> getSimilarLocales(Locale locale) {
        return SIMILAR_LOCALES.get(locale);
    }
}