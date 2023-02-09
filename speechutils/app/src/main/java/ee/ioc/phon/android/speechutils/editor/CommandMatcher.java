package ee.ioc.phon.android.speechutils.editor;

import java.util.regex.Pattern;

public interface CommandMatcher {

    /**
     * @param locale  locale
     * @param service service
     * @param app     app
     * @return true iff the given patterns match
     */
    boolean matches(Pattern locale, Pattern service, Pattern app);

}
