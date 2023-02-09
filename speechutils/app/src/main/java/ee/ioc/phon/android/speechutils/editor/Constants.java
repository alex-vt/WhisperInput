package ee.ioc.phon.android.speechutils.editor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class Constants {

    // Symbols that should not be preceded by space in a written text.
    public static final Set<Character> CHARACTERS_STICKY_LEFT =
            new HashSet<>(Arrays.asList(',', ':', ';', '.', '!', '?', '-', ')'));

    // Symbols after which the next word should be capitalized.
    // We include ) because ;-) often finishes a sentence.
    public static final Set<Character> CHARACTERS_EOS =
            new HashSet<>(Arrays.asList('.', '!', '?', ')'));

    // These symbols stick to the next symbol, i.e. no whitespace is added in front of the
    // following string.
    public static final Set<Character> CHARACTERS_STICKY_RIGHT =
            new HashSet<>(Arrays.asList('(', '[', '{', '<', '-'));

    // TODO: review these
    public static final int REWRITE_PATTERN_FLAGS = Pattern.CASE_INSENSITIVE
            | Pattern.UNICODE_CASE
            | Pattern.MULTILINE
            | Pattern.DOTALL;

    // Characters that are transparent (but not whitespace) when deciding if a word follows an EOS
    // character and should thus be capitalized.
    private static final Set<Character> CHARACTERS_TRANSPARENT =
            new HashSet<>(Arrays.asList('(', '[', '{', '<'));

    public static final boolean isTransparent(char c) {
        return Constants.CHARACTERS_TRANSPARENT.contains(c) || Character.isWhitespace(c);
    }
}