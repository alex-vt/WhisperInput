package ee.ioc.phon.android.speechutils.utils;

import ee.ioc.phon.android.speechutils.editor.Constants;

public class TextUtils {

    private TextUtils() {
    }

    /**
     * Pretty-prints the string returned by the server to be orthographically correct (Estonian),
     * assuming that the string represents a sequence of tokens separated by a single space character.
     * Note that a text editor (which has additional information about the context of the cursor)
     * will need to do additional pretty-printing, e.g. capitalization if the cursor follows a
     * sentence end marker.
     *
     * @param str String to be pretty-printed
     * @return Pretty-printed string (never null)
     */
    public static String prettyPrint(String str) {
        boolean isSentenceStart = false;
        boolean isWhitespaceBefore = false;
        String text = "";
        for (String tok : str.split(" ")) {
            if (tok.length() == 0) {
                continue;
            }
            String glue = " ";
            char firstChar = tok.charAt(0);
            if (isWhitespaceBefore
                    || Character.isWhitespace(firstChar)
                    || Constants.CHARACTERS_STICKY_LEFT.contains(firstChar)) {
                glue = "";
            }

            if (isSentenceStart) {
                tok = Character.toUpperCase(firstChar) + tok.substring(1);
            }

            if (text.length() == 0) {
                text = tok;
            } else {
                text += glue + tok;
            }

            isWhitespaceBefore = Character.isWhitespace(firstChar);

            // If the token is not a character then we are in the middle of the sentence.
            // If the token is an EOS character then a new sentences has started.
            // If the token is some other character other than whitespace (then we are in the
            // middle of the sentences. (The whitespace characters are transparent.)
            if (tok.length() > 1) {
                isSentenceStart = false;
            } else if (Constants.CHARACTERS_EOS.contains(firstChar)) {
                isSentenceStart = true;
            } else if (!isWhitespaceBefore) {
                isSentenceStart = false;
            }
        }
        return text;
    }
}