package ee.ioc.phon.android.speechutils.editor;

import android.view.inputmethod.ExtractedText;

import java.util.Collection;
import java.util.Deque;
import java.util.List;

/**
 * Note that "cursor" is the same as "selection" in Android, i.e. a cursor has a start and end position
 * which are not necessarily identical.
 * Note that the meta commands (undo, etc.) cannot be applied to some commands,
 * e.g. IME and context menu actions.
 * Most methods return an operation (op), which when run returns an undo operation.
 */
public interface CommandEditor {

    // Go to the character at the given position.
    // Negative positions start counting from the end of the text, e.g.
    // -1 == end of text
    Op moveAbs(int pos);

    /**
     * Move the cursor forward (+) or backwards (-) by the given number of characters.
     * Forward movement starts from the end of the selection, backward movement from the beginning of
     * the selection. In case of 0, the selection is reset to its end position.
     *
     * @param numOfChars number of character positions
     * @return Op
     */
    Op moveRel(int numOfChars);

    /**
     * Change either the start or the end of the selection by the given number of characters.
     *
     * @param numOfChars number of character positions
     * @param type       0 for start, 1 for end
     * @return Op
     */
    Op moveRelSel(int numOfChars, int type);

    // Press Up-arrow key
    Op keyUp();

    // Press Down-arrow key
    Op keyDown();

    // Press Left-arrow key
    Op keyLeft();

    // Press Right-arrow key
    Op keyRight();

    // Press the key with the given code
    Op keyCode(int code);

    // Press the key with the given symbolic name
    Op keyCodeStr(String codeAsStr);

    // Move cursor left to the matching string
    // Supports function @sel() to refer to the content of the current selection.
    Op select(String str);

    /**
     * Move cursor left to the matching regex.
     * Supports function @sel() to refer to the content of the current selection.
     *
     * @param regex regular expression to be searched
     * @return Op
     */
    Op selectReBefore(String regex);

    /**
     * Move cursor right to the Nth matching regex.
     * Supports function @sel() to refer to the content of the current selection.
     *
     * @param regex regular expression to be searched
     * @param n     number of matches before stopping the search
     * @return Op
     */
    Op selectReAfter(String regex, int n);

    // Extend the cursor to match the given regex
    Op selectRe(String regex, boolean applyToSelection);

    // Select all (note: not a context menu action)
    Op selectAll();

    // selectAll + replace cursor with empty string
    Op deleteAll();

    // TODO: clarify
    // Apply a regular expression to the selection,
    // and replace the matches with the given replacement string.
    Op replaceSelRe(String regex, String repl);

    // Cut (Context menu action)
    Op cut();

    // Copy (Context menu action)
    Op copy();

    // Paste (Context menu action)
    Op paste();

    // selectAll + cut
    Op cutAll();

    // selectAll + copy
    Op copyAll();

    /**
     * Delete the given numbers of character either to the left and to the right of the cursor.
     * In case there is a selection, then the selection is deleted instead.
     *
     * @param numOfChars number of chars to delete, if negative then from the left, if positive then from the right
     * @return Op
     */
    Op deleteChars(int numOfChars);

    // Delete the word immediately to the left.
    // In case there is a selection, then the selection is deleted.
    Op deleteLeftWord();

    // Replace text1 (left of cursor) with text2.
    // Deletion can be performed by setting text2 to be an empty string.
    Op replace(String text1, String text2);

    // Replace cursor with the given text
    // Supports function @sel() to refer to the content of the current selection.
    Op replaceSel(String text);

    // Replace cursor with the given text.
    // Supports function @sel() to refer to the content of the current selection.
    // Sets a new selection within the replacement using the first group of the matching regex.
    Op replaceSel(String text, String regex);

    // Uppercase the text under the cursor
    Op ucSel();

    // Lowercase the text under the cursor
    Op lcSel();

    // Interpret the text under the cursor as an integer and increase it by 1
    Op incSel();

    // Perform the given IME action
    Op imeAction(int editorAction);

    // Jump to the previous field (IME action)
    Op imeActionPrevious();

    // Jump to the next field (IME action)
    Op imeActionNext();

    // Done (IME action)
    Op imeActionDone();

    // Go (IME action)
    Op imeActionGo();

    // Search (IME action)
    Op imeActionSearch();

    // Send (IME action)
    Op imeActionSend();

    // Undo the last N commands (or text entries)
    Op undo(int n);

    // Combine the last N commands
    Op combine(int n);

    // Apply the last command N times
    Op apply(int n);

    // Start an activity from the given JSON-encoded Android Intent.
    // Supports function @sel() to refer to the content of the current selection.
    Op activity(String json);

    // Replace cursor with the response of the given URL.
    // The arg (if not null) is encoded and concatenated to the URL.
    // Executed by AsyncTask.
    // E.g. url == "http://api.mathjs.org/v4/?expr="; arg == "@sel()+1"
    Op getUrl(String url, String arg);

    // Commands that are not exposed to the end-user in CommandEditorManager

    CommandEditorResult commitFinalResult(String text);

    boolean commitPartialResult(String text);

    boolean runOp(Op op);

    boolean runOp(Op op, boolean undoable);

    ExtractedText getExtractedText();

    CharSequence getText();

    List<UtteranceRewriter> getRewriters();

    void setRewriters(List<UtteranceRewriter> urs);

    Deque<Op> getOpStack();

    Deque<Op> getUndoStack();

    Op combineOps(Collection<Op> ops);

    /**
     * Convert the given text into an op, if some rewrite rules trigger on it.
     * Otherwise return null, unless always==true.
     *
     * @param text   Input text
     * @param always Return Op also if no rewrite triggers
     * @return Op or null
     */
    Op getOpOrNull(String text, boolean always);

    void reset();
}