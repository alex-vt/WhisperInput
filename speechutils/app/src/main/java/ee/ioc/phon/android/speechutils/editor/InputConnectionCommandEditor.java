package ee.ioc.phon.android.speechutils.editor;

import static android.os.Build.VERSION_CODES;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.text.TextUtils;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;

import org.json.JSONException;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ee.ioc.phon.android.speechutils.Log;
import ee.ioc.phon.android.speechutils.utils.HttpUtils;
import ee.ioc.phon.android.speechutils.utils.IntentUtils;
import ee.ioc.phon.android.speechutils.utils.JsonUtils;

/**
 * TODO: this is work in progress
 * TODO: keep track of added spaces
 * TODO: the returned Op should never be null, however, run can return a null Op
 */
public class InputConnectionCommandEditor implements CommandEditor {

    // Maximum number of previous utterances that a command can contain
    private static final int MAX_UTT_IN_COMMAND = 3;

    // Maximum number of characters that left-swipe is willing to delete
    private static final int MAX_DELETABLE_CONTEXT = 100;
    // Token optionally preceded by whitespace
    private static final Pattern WHITESPACE_AND_TOKEN = Pattern.compile("\\s*\\w+");
    private static final String F_SELECTION = "@sel()";
    private static final Pattern F_TIMESTAMP = Pattern.compile("@timestamp\\(([^,]+), *([^,]+)\\)");

    private Context mContext;

    private CharSequence mTextBeforeCursor;
    // TODO: Restrict the size of these stacks

    // The command prefix is a list of consecutive final results whose concatenation can possibly
    // form a command. An item is added to the list for every final result that is not a command.
    // The list if cleared if a command is executed.
    private List<String> mCommandPrefix = new ArrayList<>();
    private Deque<Op> mOpStack = new ArrayDeque<>();
    private Deque<Op> mUndoStack = new ArrayDeque<>();

    private InputConnection mInputConnection;

    private List<UtteranceRewriter> mRewriters;

    public InputConnectionCommandEditor(@NonNull Context context) {
        mContext = context;
    }

    public void setInputConnection(@NonNull InputConnection inputConnection) {
        mInputConnection = inputConnection;
    }

    protected @NonNull
    InputConnection getInputConnection() {
        return mInputConnection;
    }

    @Override
    public void setRewriters(List<UtteranceRewriter> urs) {
        mRewriters = urs;
        reset();
    }

    @Override
    public List<UtteranceRewriter> getRewriters() {
        return mRewriters;
    }

    @Override
    public boolean runOp(Op op) {
        return runOp(op, true);
    }

    @Override
    public boolean runOp(Op op, boolean undoable) {
        // TODO: why does this happen
        //if (op == null) {
        //    return false;
        //}
        reset();
        Op undo = op.run();
        if (undo == null) {
            // Operation failed;
            return false;
        }
        if (undoable && !undo.isNoOp()) {
            pushOp(op);
            pushOpUndo(undo);
        }
        mTextBeforeCursor = getTextBeforeCursor();
        return true;
    }

    @Override
    public Op getOpOrNull(@NonNull final String text, boolean always) {
        String newText = text;
        for (UtteranceRewriter ur : mRewriters) {
            if (ur == null) {
                continue;
            }
            UtteranceRewriter.Rewrite rewrite = ur.getRewrite(text);
            newText = rewrite.mStr;
            if (rewrite.isCommand()) {
                CommandEditorManager.EditorCommand ec = CommandEditorManager.get(rewrite.mId);
                if (ec == null) {
                    return null;
                } else {
                    if (newText.isEmpty()) {
                        return ec.getOp(this, rewrite.mArgs);
                    }
                    // If is command, then the 2 ops will be combined.
                    List<Op> ops = new ArrayList<>();
                    ops.add(getCommitWithOverwriteOp(newText, true));
                    ops.add(ec.getOp(this, rewrite.mArgs));
                    return combineOps(ops);
                }
            }
        }
        if (always || !text.equals(newText)) {
            return getCommitWithOverwriteOp(newText, false);
        }
        return null;
    }

    @Override
    public CommandEditorResult commitFinalResult(final String text) {
        CommandEditorResult result = null;
        if (mRewriters == null || mRewriters.isEmpty()) {
            // If rewrites/commands are not defined (default), then selection can be dictated over.
            commitWithOverwrite(text, true);
        } else {
            final ExtractedText et = getExtractedText();
            final String selectedText = getSelectedText();
            // Try to interpret the text as a command and if it is, then apply it.
            // Otherwise write out the text as usual.
            UtteranceRewriter.Rewrite rewrite = applyCommand(text);
            String textRewritten = rewrite.mStr;
            final int len = maybeCommit(textRewritten, !selectedText.isEmpty() && rewrite.isCommand());
            // TODO: add undo for setSelection even if len==0
            if (len > 0) {
                pushOpUndo(new Op("delete " + len) {
                    @Override
                    public Op run() {
                        mInputConnection.beginBatchEdit();
                        boolean success = deleteSurrounding(len, 0);
                        if (et != null && selectedText.length() > 0) {
                            success = mInputConnection.commitText(selectedText, 1) &&
                                    mInputConnection.setSelection(et.selectionStart, et.selectionEnd);
                        }
                        mInputConnection.endBatchEdit();
                        if (success) {
                            return NO_OP;
                        }
                        return null;
                    }
                });
            }
            boolean success = false;
            if (rewrite.isCommand()) {
                CommandEditorManager.EditorCommand ec = CommandEditorManager.get(rewrite.mId);
                if (ec != null) {
                    // TODO: dont call runOp from here
                    success = runOp(ec.getOp(this, rewrite.mArgs));
                }
            } else {
                mCommandPrefix.add(textRewritten);
            }
            result = new CommandEditorResult(success, rewrite);
        }
        return result;
    }

    /**
     * Sets the text as "composing text" unless there is a selection.
     */
    @Override
    public boolean commitPartialResult(String text) {
        CharSequence cs = mInputConnection.getSelectedText(0);
        if (cs != null && cs.length() > 0) {
            return false;
        }

        String newText = text;
        if (mRewriters != null && !mRewriters.isEmpty()) {
            for (UtteranceRewriter ur : mRewriters) {
                if (ur == null) {
                    continue;
                }
                UtteranceRewriter.Rewrite rewrite = ur.getRewrite(newText);
                newText = rewrite.mStr;
                if (rewrite.isCommand()) {
                    break;
                }
            }
        }
        commitWithOverwrite(newText, false);
        return true;
    }

    @Override
    public CharSequence getText() {
        ExtractedText et = getExtractedText();
        if (et == null) {
            return null;
        }
        return et.text;
    }

    @Override
    public void reset() {
        mCommandPrefix.clear();
        mTextBeforeCursor = getTextBeforeCursor();
    }

    @Override
    public Op activity(final String json) {
        return new Op("activity") {
            @Override
            public Op run() {
                Op undo = null;
                try {
                    if (IntentUtils.startActivityIfAvailable(mContext, JsonUtils.createIntent(json.replace(F_SELECTION, getSelectedText())))) {
                        undo = NO_OP;
                    }
                } catch (JSONException e) {
                    Log.i("startSearchActivity: JSON: " + e.getMessage());
                }
                return undo;
            }
        };
    }

    @Override
    public Op getUrl(final String url, final String arg) {
        return new Op("getUrl") {
            @Override
            public Op run() {
                String selectedText = getSelectedText();
                final String url1;
                if (arg != null && !arg.isEmpty()) {
                    url1 = url.replace(F_SELECTION, selectedText) + HttpUtils.encode(arg.replace(F_SELECTION, selectedText));
                } else {
                    url1 = url.replace(F_SELECTION, selectedText);
                }
                new AsyncTask<String, Void, String>() {

                    @Override
                    protected String doInBackground(String... urls) {
                        try {
                            return HttpUtils.getUrl(urls[0]);
                        } catch (IOException e) {
                            return "[ERROR: Unable to retrieve " + urls[0] + ": " + e.getLocalizedMessage() + "]";
                        }
                    }

                    @Override
                    protected void onPostExecute(String result) {
                        runOp(replaceSel(result));
                    }
                }.execute(url1);
                return Op.NO_OP;
            }
        };
    }

    @Override
    public Op keyUp() {
        return new Op("goUp") {
            @Override
            public Op run() {
                if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))) {
                    return keyDown();
                }
                return null;
            }
        };
    }

    @Override
    public Op keyDown() {
        return new Op("goDown") {
            @Override
            public Op run() {
                if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))) {
                    return keyUp();
                }
                return null;
            }
        };
    }

    @Override
    public Op keyLeft() {
        return new Op("goLeft") {
            @Override
            public Op run() {
                if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))) {
                    return keyRight();
                }
                return null;
            }
        };
    }

    @Override
    public Op keyRight() {
        return new Op("goRight") {
            @Override
            public Op run() {
                if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))) {
                    return keyLeft();
                }
                return null;
            }
        };
    }

    /**
     * Returns an operation that pops the undo stack the given number of times,
     * executing each popped op. If the stack does not contain the given number of elements, or
     * one of the ops fails, then returns null. Otherwise returns NO_OP.
     */
    @Override
    public Op undo(final int steps) {
        return new Op("undo") {
            @Override
            public Op run() {
                Op undo = Op.NO_OP;
                mInputConnection.beginBatchEdit();
                for (int i = 0; i < steps; i++) {
                    try {
                        Op op = mUndoStack.pop().run();
                        if (op == null) {
                            undo = null;
                            break;
                        }
                    } catch (NoSuchElementException ex) {
                        undo = null;
                        break;
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    /**
     * The combine operation modifies the stack by removing the top n elements and adding
     * their combination instead.
     * TODO: implement undo
     */
    @Override
    public Op combine(final int steps) {
        return new Op("combine " + steps) {
            @Override
            public Op run() {
                final Deque<Op> combination = new ArrayDeque<>();
                try {
                    for (int i = 0; i < steps; i++) {
                        combination.push(mOpStack.pop());
                    }
                } catch (NoSuchElementException e) {
                    return null;
                }
                mOpStack.push(combineOps(combination));
                return NO_OP;
            }
        };
    }

    @Override
    public Op apply(final int steps) {
        return new Op("apply") {
            @Override
            public Op run() {
                Op op = mOpStack.peek();
                if (op == null) {
                    return null;
                }
                final Deque<Op> combination = new ArrayDeque<>();
                mInputConnection.beginBatchEdit();
                for (int i = 0; i < steps; i++) {
                    Op undo = op.run();
                    if (undo == null) {
                        break;
                    }
                    combination.push(undo);
                }
                mInputConnection.endBatchEdit();
                return new Op("undo apply", combination.size()) {

                    @Override
                    public Op run() {
                        mInputConnection.beginBatchEdit();
                        while (!combination.isEmpty()) {
                            combination.pop().run();
                        }
                        mInputConnection.endBatchEdit();
                        return NO_OP;
                    }
                };
            }
        };
    }

    @Override
    public Op moveAbs(final int pos) {
        return new Op("moveAbs " + pos) {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                ExtractedText et = getExtractedText();
                if (et != null) {
                    int charPos = pos;
                    if (pos < 0) {
                        //-1 == end of text
                        charPos = et.text.length() + pos + 1;
                    }
                    undo = getOpSetSelection(charPos, charPos, et.selectionStart, et.selectionEnd).run();
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op cut() {
        return getContextMenuActionOp(android.R.id.cut);
    }

    @Override
    public Op copy() {
        return getContextMenuActionOp(android.R.id.copy);
    }

    @Override
    public Op paste() {
        return getContextMenuActionOp(android.R.id.paste);
    }

    /**
     * mInputConnection.performContextMenuAction(android.R.id.selectAll) does not create a selection
     */
    @Override
    public Op selectAll() {
        return new Op("selectAll") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                final ExtractedText et = getExtractedText();
                if (et != null) {
                    undo = getOpSetSelection(0, et.text.length(), et.selectionStart, et.selectionEnd).run();
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    // Not undoable
    @Override
    public Op cutAll() {
        Collection<Op> collection = new ArrayList<>();
        collection.add(selectAll());
        collection.add(cut());
        return combineOps(collection);
    }

    // Not undoable
    @Override
    public Op deleteAll() {
        return new Op("deleteAll") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                Op op = selectAll().run();
                if (op != null) {
                    if (mInputConnection.commitText("", 0)) {
                        undo = NO_OP;
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    // TODO: test with failing ops
    @Override
    public Op combineOps(final Collection<Op> ops) {
        return new Op(ops.toString(), ops.size()) {
            @Override
            public Op run() {
                final Deque<Op> combination = new ArrayDeque<>();
                mInputConnection.beginBatchEdit();
                for (Op op : ops) {
                    if (op != null && !op.isNoOp()) {
                        Op undo = op.run();
                        if (undo == null) {
                            break;
                        }
                        combination.push(undo);
                    }
                }
                mInputConnection.endBatchEdit();
                return new Op(combination.toString(), combination.size()) {
                    @Override
                    public Op run() {
                        mInputConnection.beginBatchEdit();
                        while (!combination.isEmpty()) {
                            Op undo1 = combination.pop().run();
                            if (undo1 == null) {
                                break;
                            }
                        }
                        mInputConnection.endBatchEdit();
                        return combineOps(ops);
                    }
                };
            }
        };
    }

    // Not undoable
    @Override
    public Op copyAll() {
        Collection<Op> collection = new ArrayList<>();
        collection.add(selectAll());
        collection.add(copy());
        return combineOps(collection);
    }

    /**
     * Deletes all characters up to the leftmost whitespace from the cursor (including the whitespace).
     * If something is selected then delete the selection.
     */
    @Override
    public Op deleteLeftWord() {
        return new Op("deleteLeftWord") {
            @Override
            public Op run() {
                Op undo = null;
                boolean success = false;
                mInputConnection.beginBatchEdit();
                // If something is selected then delete the selection and return
                final String oldText = getSelectedText();
                if (oldText.length() > 0) {
                    undo = getCommitTextOp(oldText, "").run();
                } else {
                    final CharSequence beforeCursor = mInputConnection.getTextBeforeCursor(MAX_DELETABLE_CONTEXT, 0);
                    if (beforeCursor != null) {
                        final int beforeCursorLength = beforeCursor.length();
                        Matcher m = WHITESPACE_AND_TOKEN.matcher(beforeCursor);
                        int lastIndex = 0;
                        while (m.find()) {
                            // If the cursor is immediately left from WHITESPACE_AND_TOKEN, then
                            // delete the WHITESPACE_AND_TOKEN, otherwise delete whatever is in between.
                            lastIndex = beforeCursorLength == m.end() ? m.start() : m.end();
                        }
                        if (lastIndex > 0) {
                            success = deleteSurrounding(beforeCursorLength - lastIndex, 0);
                        } else if (beforeCursorLength < MAX_DELETABLE_CONTEXT) {
                            success = deleteSurrounding(beforeCursorLength, 0);
                        }
                        if (success) {
                            mInputConnection.endBatchEdit();
                            final CharSequence cs = lastIndex > 0 ? beforeCursor.subSequence(lastIndex, beforeCursorLength) : beforeCursor;
                            undo = new Op("commitText: " + cs) {
                                @Override
                                public Op run() {
                                    if (mInputConnection.commitText(cs, 1)) {
                                        return NO_OP;
                                    }
                                    return null;
                                }
                            };
                        }
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op select(final String query) {
        return new Op("select " + query) {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                ExtractedText et = getExtractedText();
                if (et != null) {
                    Pair<Integer, CharSequence> queryResult = lastIndexOf(query.replace(F_SELECTION, getSelectedText()), et);
                    if (queryResult.first >= 0) {
                        undo = getOpSetSelection(queryResult.first, queryResult.first + queryResult.second.length(), et.selectionStart, et.selectionEnd).run();
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    /**
     * Returns the current selection wrapped in regex quotation.
     */
    private CharSequence getSelectionAsRe(ExtractedText et) {
        if (et.selectionStart == et.selectionEnd) {
            return "";
        }
        return "\\Q" + et.text.subSequence(et.selectionStart, et.selectionEnd) + "\\E";
    }

    @Override
    public Op selectReBefore(final String regex) {
        return new Op("selectReBefore") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                final ExtractedText et = getExtractedText();
                if (et != null) {
                    CharSequence input = et.text.subSequence(0, et.selectionStart);
                    // 0 == last match
                    Pair<Integer, Integer> pos = matchNth(Pattern.compile(regex.replace(F_SELECTION, getSelectionAsRe(et))), input, 0);
                    if (pos != null) {
                        undo = getOpSetSelection(pos.first, pos.second, et.selectionStart, et.selectionEnd).run();
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op selectReAfter(final String regex, final int n) {
        return new Op("selectReAfter") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                final ExtractedText et = getExtractedText();
                if (et != null) {
                    CharSequence input = et.text.subSequence(et.selectionEnd, et.text.length());
                    // TODO: sometimes crashes with:
                    // StringIndexOutOfBoundsException: String index out of range: -4
                    Pair<Integer, Integer> pos = matchNth(Pattern.compile(regex.replace(F_SELECTION, getSelectionAsRe(et))), input, n);
                    if (pos != null) {
                        undo = getOpSetSelection(et.selectionEnd + pos.first, et.selectionEnd + pos.second, et.selectionStart, et.selectionEnd).run();
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op selectRe(final String regex, final boolean applyToSelection) {
        return new Op("selectRe") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                final ExtractedText et = getExtractedText();
                if (et != null) {
                    if (applyToSelection || et.selectionStart == et.selectionEnd) {
                        Pair<Integer, Integer> pos = matchAtPos(Pattern.compile(regex), et.text, et.selectionStart, et.selectionEnd);
                        if (pos != null) {
                            undo = getOpSetSelection(pos.first, pos.second, et.selectionStart, et.selectionEnd).run();
                        }
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op replace(final String query, final String replacement) {
        return new Op("replace") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                final ExtractedText et = getExtractedText();
                if (et != null) {
                    Pair<Integer, CharSequence> queryResult = lastIndexOf(query, et);
                    final CharSequence match = queryResult.second;
                    if (queryResult.first >= 0) {
                        boolean success = mInputConnection.setSelection(queryResult.first, queryResult.first);
                        if (success) {
                            // Delete existing text
                            success = deleteSurrounding(0, match.length());
                            if (replacement.isEmpty()) {
                                if (success) {
                                    undo = new Op("undo replace1") {
                                        @Override
                                        public Op run() {
                                            mInputConnection.beginBatchEdit();
                                            boolean success2 = mInputConnection.commitText(match, 1) &&
                                                    mInputConnection.setSelection(et.selectionStart, et.selectionEnd);
                                            mInputConnection.endBatchEdit();
                                            if (success2) {
                                                return NO_OP;
                                            }
                                            return null;
                                        }
                                    };
                                }
                            } else {
                                success = mInputConnection.commitText(replacement, 1);
                                if (success) {
                                    undo = new Op("undo replace2") {
                                        @Override
                                        public Op run() {
                                            mInputConnection.beginBatchEdit();
                                            boolean success2 = deleteSurrounding(replacement.length(), 0) &&
                                                    mInputConnection.commitText(match, 1) &&
                                                    mInputConnection.setSelection(et.selectionStart, et.selectionEnd);
                                            mInputConnection.endBatchEdit();
                                            if (success2) {
                                                return NO_OP;
                                            }
                                            return null;
                                        }
                                    };
                                }
                            }
                        }
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    public Op replaceSel(final String str) {
        return replaceSel(str, null);
    }

    /**
     * TODO: generalize to any functions
     */
    private String expandFuns(String line) {
        Matcher m = F_TIMESTAMP.matcher(line);
        String newLine = "";
        int pos = 0;
        Date currentTime = null;
        while (m.find()) {
            if (currentTime == null) {
                currentTime = Calendar.getInstance().getTime();
            }
            newLine += line.substring(pos, m.start());
            DateFormat df = new SimpleDateFormat(m.group(1), new Locale(m.group(2)));
            newLine += df.format(currentTime);
            pos = m.end();
        }
        if (pos == 0) {
            return line;
        }
        return newLine + line.substring(pos);
    }

    /**
     * Commits texts and creates a new selection (within the commited text).
     * TODO: fix undo
     */
    @Override
    public Op replaceSel(final String str, final String regex) {
        return new Op("replaceSel") {
            @Override
            public Op run() {
                // Replace mentions of selection with a back-reference
                mInputConnection.beginBatchEdit();
                // Change the current selection with the input argument, possibly embedding the selection.
                String selectedText = getSelectedText();
                String newText;
                if (str == null || str.isEmpty()) {
                    newText = "";
                } else {
                    newText = expandFuns(str.replace(F_SELECTION, selectedText));
                }
                Op op = null;
                if (regex != null) {
                    Pair<Integer, Integer> pair = matchNth(Pattern.compile(regex), newText, 1);
                    if (pair != null) {
                        final ExtractedText et = getExtractedText();
                        // TODO: shift by the offset whenever we use getExtractedText
                        int oldStart = et.startOffset + et.selectionStart;
                        int oldEnd = et.startOffset + et.selectionEnd;
                        Collection<Op> collection = new ArrayList<>();
                        collection.add(getCommitTextOp(selectedText, newText));
                        collection.add(getOpSetSelection(oldStart + pair.first, oldStart + pair.second, oldStart, oldEnd));
                        op = combineOps(collection);
                    }
                }
                // If no regex was provided or no match was found then just commit the replacement.
                if (op == null) {
                    op = getCommitTextOp(selectedText, newText);
                }
                Op undo = op.run();
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op replaceSelRe(final String regex, final String repl) {
        return new Op("replaceSelRe") {
            @Override
            public Op run() {
                mInputConnection.beginBatchEdit();
                String selectedText = getSelectedText();
                String newText = selectedText.replaceAll(regex, repl);
                Op undo = getCommitTextOp(selectedText, newText).run();
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op ucSel() {
        return new Op("ucSel") {
            @Override
            public Op run() {
                mInputConnection.beginBatchEdit();
                String oldText = getSelectedText();
                Op undo = getCommitTextOp(oldText, oldText.toUpperCase()).run();
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op lcSel() {
        return new Op("lcSel") {
            @Override
            public Op run() {
                mInputConnection.beginBatchEdit();
                String oldText = getSelectedText();
                Op undo = getCommitTextOp(oldText, oldText.toLowerCase()).run();
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op incSel() {
        return new Op("incSel") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                String oldText = getSelectedText();
                try {
                    undo = getCommitTextOp(oldText, String.valueOf(Integer.parseInt(oldText) + 1)).run();
                } catch (NumberFormatException e) {
                    // Intentional
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    // TODO: share code with deleteLeftWord
    @Override
    public Op deleteChars(final int numOfChars) {
        return new Op("deleteChars") {
            @Override
            public Op run() {
                Op undo = null;
                boolean success;
                mInputConnection.beginBatchEdit();
                // If something is selected then delete the selection and return
                final String oldText = getSelectedText();
                if (oldText.length() > 0) {
                    undo = getCommitTextOp(oldText, "").run();
                } else {
                    if (numOfChars != 0) {
                        final int num;
                        final CharSequence cs;
                        if (numOfChars < 0) {
                            num = -1 * numOfChars;
                            cs = mInputConnection.getTextBeforeCursor(num, 0);
                        } else {
                            num = numOfChars;
                            cs = mInputConnection.getTextAfterCursor(num, 0);
                        }
                        if (cs != null && cs.length() == num) {
                            final int newCursorPos;
                            if (numOfChars < 0) {
                                success = deleteSurrounding(num, 0);
                                newCursorPos = 1;
                            } else {
                                success = deleteSurrounding(0, num);
                                newCursorPos = 0;
                            }
                            if (success) {
                                mInputConnection.endBatchEdit();
                                undo = new Op("commitText: " + cs) {
                                    @Override
                                    public Op run() {
                                        if (mInputConnection.commitText(cs, newCursorPos)) {
                                            return NO_OP;
                                        }
                                        return null;
                                    }
                                };
                            }
                        }
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    /**
     * Not undoable
     *
     * @param code key code
     * @return op that sends the given code
     */
    @Override
    public Op keyCode(final int code) {
        return new Op("keyCode") {
            @Override
            public Op run() {
                if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code))) {
                    return Op.NO_OP;
                }
                return null;
            }
        };
    }

    /**
     * Not undoable
     *
     * @param symbolicName key code's symbolic name
     * @return op that sends the given code
     */
    @Override
    public Op keyCodeStr(String symbolicName) {
        int code = KeyEvent.keyCodeFromString("KEYCODE_" + symbolicName);
        if (code != KeyEvent.KEYCODE_UNKNOWN) {
            return keyCode(code);
        }
        return null;
    }

    /**
     * There is no undo.
     */
    @Override
    public Op imeAction(int editorAction) {
        // TODO: map the given Id to a label that uses the symbolic name like NEXT
        return getEditorActionOp(editorAction, "IME_ACTION_" + editorAction);
    }

    /**
     * There is no undo, because the undo-stack does not survive the jump to another field.
     */
    @Override
    public Op imeActionPrevious() {
        return getEditorActionOp(EditorInfo.IME_ACTION_PREVIOUS, "IME_ACTION_PREVIOUS");
    }

    /**
     * There is no undo, because the undo-stack does not survive the jump to another field.
     */
    @Override
    public Op imeActionNext() {
        return getEditorActionOp(EditorInfo.IME_ACTION_NEXT, "IME_ACTION_NEXT");
    }

    @Override
    public Op imeActionDone() {
        // Does not work on Google Searchbar
        return getEditorActionOp(EditorInfo.IME_ACTION_DONE, "IME_ACTION_DONE");
    }

    @Override
    public Op imeActionGo() {
        // Works in Google Searchbar, GF Translator, but NOT in the Firefox search widget
        return getEditorActionOp(EditorInfo.IME_ACTION_GO, "IME_ACTION_GO");
    }

    @Override
    public Op imeActionSearch() {
        return getEditorActionOp(EditorInfo.IME_ACTION_SEARCH, "IME_ACTION_SEARCH");
    }

    @Override
    public Op imeActionSend() {
        return getEditorActionOp(EditorInfo.IME_ACTION_SEND, "IME_ACTION_SEND");
    }

    @Override
    public Deque<Op> getOpStack() {
        return mOpStack;
    }

    @Override
    public Deque<Op> getUndoStack() {
        return mUndoStack;
    }

    @Override
    public ExtractedText getExtractedText() {
        return mInputConnection.getExtractedText(new ExtractedTextRequest(), 0);
    }

    private void pushOp(Op op) {
        mOpStack.push(op);
        Log.i("undo: push op: " + mOpStack.toString());
    }

    private void pushOpUndo(Op op) {
        mUndoStack.push(op);
        Log.i("undo: push undo: " + mUndoStack.toString());
    }

    private Op getEditorActionOp(final int editorAction, String label) {
        return new Op(label) {
            @Override
            public Op run() {
                if (mInputConnection.performEditorAction(editorAction)) {
                    return Op.NO_OP;
                }
                return null;
            }
        };
    }

    private Op getContextMenuActionOp(final int contextMenuAction) {
        return new Op("contextMenuAction " + contextMenuAction) {
            @Override
            public Op run() {
                if (mInputConnection.performContextMenuAction(contextMenuAction)) {
                    return Op.NO_OP;
                }
                return null;
            }
        };
    }

    /**
     * Updates the text field, modifying only the parts that have changed.
     * Adds text at the cursor, possibly overwriting a selection.
     * Returns the number of characters added.
     */
    private int commitWithOverwrite(String text, boolean isCommitText) {
        mInputConnection.beginBatchEdit();
        text = getGlue(text, mTextBeforeCursor) + capitalizeIfNeeded(text, mTextBeforeCursor);
        if (isCommitText) {
            mInputConnection.commitText(text, 1);
            if (!text.isEmpty()) {
                // This seems to work correctly, regardless of the length of the added text,
                // i.e. we do not need to call (the expensive) getTextBeforeCursor() here.
                mTextBeforeCursor = text;
            }
        } else {
            // We let the editor define the style of the composing text.
            //Spannable ss = new SpannableString(text);
            //ss.setSpan(SPAN_PARTIAL_RESULTS, 0, text.length(), Spanned.SPAN_COMPOSING);
            mInputConnection.setComposingText(text, 1);
        }
        mInputConnection.endBatchEdit();
        return text.length();
    }

    /**
     * We look at the left context of the cursor
     * to decide which glue symbol to use and whether to capitalize the text.
     * Note that also the composing text (set by partial results) moves the cursor.
     */
    private CharSequence getTextBeforeCursor() {
        return mInputConnection.getTextBeforeCursor(MAX_DELETABLE_CONTEXT, 0);
    }

    /**
     * TODO: review
     * we should be able to review the last N ops and undo them if they can be interpreted as
     * a combined op.
     */
    private Op getCommitWithOverwriteOp(final String text, final boolean isCommand) {
        return new Op("add " + text) {
            @Override
            public Op run() {
                mInputConnection.beginBatchEdit();
                final ExtractedText et = getExtractedText();
                final String selectedText = getSelectedText();
                if (text.isEmpty() && !selectedText.isEmpty() && isCommand) {
                    mInputConnection.endBatchEdit();
                    return null;
                }
                final int addedLength = commitWithOverwrite(text, true);
                mInputConnection.endBatchEdit();
                return new Op("delete " + addedLength) {
                    @Override
                    public Op run() {
                        mInputConnection.beginBatchEdit();
                        boolean success = deleteSurrounding(addedLength, 0);
                        if (et != null && selectedText.length() > 0) {
                            success = mInputConnection.commitText(selectedText, 1) &&
                                    mInputConnection.setSelection(et.selectionStart, et.selectionEnd);
                        }
                        mInputConnection.endBatchEdit();
                        if (success) {
                            return NO_OP;
                        }
                        return null;
                    }
                };
            }
        };
    }

    /**
     * If there is a selection, and the input is a command with no replacement text
     * then do not replace the selection with an empty string, because the command
     * needs to apply to the selection, e.g. uppercase it.
     *
     * @param text      Text to be commited
     * @param condition Condition in addition to text begin empty to block the commit
     * @return Length of the added text
     */
    private int maybeCommit(String text, boolean condition) {
        if (text.isEmpty() && condition) {
            return 0;
        }
        return commitWithOverwrite(text, true);
    }

    /**
     * Op that commits a text at the cursor. If successful then an undo is returned which deletes
     * the text and restores the old selection.
     */
    private Op getCommitTextOp(final CharSequence oldText, final CharSequence newText) {
        return new Op("commitText") {
            @Override
            public Op run() {
                Op undo = null;
                // TODO: use getSelection
                final ExtractedText et = getExtractedText();
                if (mInputConnection.commitText(newText, 1)) {
                    undo = new Op("deleteSurroundingText+commitText") {
                        @Override
                        public Op run() {
                            mInputConnection.beginBatchEdit();
                            boolean success = deleteSurrounding(newText.length(), 0);
                            if (success && oldText != null) {
                                success = mInputConnection.commitText(oldText, 1);
                            }
                            if (et != null && success) {
                                success = mInputConnection.setSelection(et.selectionStart, et.selectionEnd);
                            }
                            mInputConnection.endBatchEdit();
                            if (success) {
                                return NO_OP;
                            }
                            return null;
                        }
                    };
                }
                return undo;
            }
        };
    }

    private Op getOpSetSelection(final int i, final int j, final int oldSelectionStart, final int oldSelectionEnd) {
        return new Op("setSelection") {
            @Override
            public Op run() {
                Op undo = null;
                if (mInputConnection.setSelection(i, j)) {
                    undo = new Op("setSelection") {
                        @Override
                        public Op run() {
                            if (mInputConnection.setSelection(oldSelectionStart, oldSelectionEnd)) {
                                return NO_OP;
                            }
                            return null;
                        }
                    };
                }
                return undo;
            }
        };
    }

    /**
     * Tries to match a substring before the cursor, using case-insensitive matching.
     * TODO: this might not work with some Unicode characters
     *
     * @param query search string
     * @param et    text to search from
     * @return pair index of the last occurrence of the match, and the matched string
     */
    private Pair<Integer, CharSequence> lastIndexOf(String query, ExtractedText et) {
        int start = et.selectionStart;
        query = query.toLowerCase();
        CharSequence input = et.text.subSequence(0, start);
        CharSequence match = null;
        int index = input.toString().toLowerCase().lastIndexOf(query);
        if (index >= 0) {
            match = input.subSequence(index, index + query.length());
        }
        return new Pair<>(index, match);
    }

    /**
     * Go to the Nth match and return the indices of the 1st group in the match if available.
     * If not then return the indices of the whole match.
     * If the match could not be made N times the return the last match (e.g. if n == 0).
     * If no match was found then return {@code null}.
     * TODO: if possible, support negative N to match from end to beginning.
     */
    private Pair<Integer, Integer> matchNth(Pattern pattern, CharSequence input, int n) {
        Matcher matcher = pattern.matcher(input);
        Pair<Integer, Integer> pos = null;
        int end = 0;
        int counter = 0;
        while (matcher.find(end)) {
            counter++;
            int group = 0;
            if (matcher.groupCount() > 0) {
                group = 1;
            }
            pos = new Pair<>(matcher.start(group), matcher.end(group));
            if (counter == n) {
                break;
            }
            int newEnd = matcher.end(group);
            // We require the end position to increase to avoid infinite loop when matching ^.
            if (newEnd <= end) {
                end++;
                if (end >= input.length()) {
                    break;
                }
            } else {
                end = newEnd;
            }
        }
        return pos;
    }

    private Pair<Integer, Integer> matchAtPos(Pattern pattern, CharSequence input, int posStart, int posEnd) {
        Matcher matcher = pattern.matcher(input);
        Pair<Integer, Integer> pos = null;
        while (matcher.find()) {
            int group = 0;
            if (matcher.groupCount() > 0) {
                group = 1;
            }
            if (matcher.start(group) <= posStart) {
                if (posEnd <= matcher.end(group)) {
                    return new Pair<>(matcher.start(group), matcher.end(group));
                }
            } else {
                // Stop searching if the match start only after the cursor.
                return null;
            }
        }
        return pos;
    }

    private String getSelectedText() {
        CharSequence cs = mInputConnection.getSelectedText(0);
        if (cs == null || cs.length() == 0) {
            return "";
        }
        return cs.toString();
    }

    @Override
    public Op moveRel(final int numOfChars) {
        return move(numOfChars, 2);
    }


    @Override
    public Op moveRelSel(final int numOfChars, final int type) {
        return move(numOfChars, type);
    }

    private Op move(final int numberOfChars, final int type) {
        return new Op("moveRel") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                ExtractedText extractedText = getExtractedText();
                if (extractedText != null) {
                    int newStart = extractedText.selectionStart;
                    int newEnd = extractedText.selectionEnd;
                    if (type == 0) {
                        newStart += numberOfChars;
                    } else if (type == 1) {
                        newEnd += numberOfChars;
                    } else {
                        if (numberOfChars < 0) {
                            newStart += numberOfChars;
                            newEnd = newStart;
                        } else {
                            newEnd += numberOfChars;
                            newStart = newEnd;
                        }
                    }
                    undo = getOpSetSelection(newStart, newEnd, extractedText.selectionStart, extractedText.selectionEnd).run();
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    /**
     * Check the last committed texts if they can be combined into a command. If so, then undo
     * these commits and return the constructed command.
     */
    private UtteranceRewriter.Rewrite applyCommand(String text) {
        int len = mCommandPrefix.size();
        for (int i = Math.min(MAX_UTT_IN_COMMAND, len); i > 0; i--) {
            List sublist = mCommandPrefix.subList(len - i, len);
            // TODO: sometimes sublist is empty?
            String possibleCommand = TextUtils.join(" ", sublist);
            if (possibleCommand.isEmpty()) {
                possibleCommand = text;
            } else {
                possibleCommand += " " + text;
            }
            Log.i("applyCommand: testing: <" + possibleCommand + ">");
            for (UtteranceRewriter ur : mRewriters) {
                if (ur == null) {
                    continue;
                }
                UtteranceRewriter.Rewrite rewrite = ur.getRewrite(possibleCommand);
                if (rewrite.isCommand()) {
                    Log.i("applyCommand: isCommand: " + possibleCommand);
                    undo(i).run();
                    return rewrite;
                }
            }
        }
        // TODO: review this
        String newText = text;
        UtteranceRewriter.Rewrite rewrite = null;
        for (UtteranceRewriter ur : mRewriters) {
            if (ur == null) {
                continue;
            }
            rewrite = ur.getRewrite(newText);
            if (rewrite.isCommand()) {
                return rewrite;
            }
            newText = rewrite.mStr;
        }
        if (rewrite == null) {
            rewrite = new UtteranceRewriter.Rewrite(newText);
        }
        return rewrite;
    }

    private boolean deleteSurrounding(int beforeLength, int afterLength) {
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            return mInputConnection.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
        }
        return mInputConnection.deleteSurroundingText(beforeLength, afterLength);
    }

    /**
     * Return capitalized text if
     * the last character of the trimmed left context is end-of-sentence marker.
     */
    private static String capitalizeIfNeeded(String text, CharSequence leftContext) {
        if (text.isEmpty()) {
            return text;
        }
        if (requiresCap(leftContext)) {
            // Since the text can start with whitespace (newline),
            // we capitalize the first non-whitespace character.
            int firstNonWhitespaceIndex = -1;
            for (int i = 0; i < text.length(); i++) {
                if (!Constants.isTransparent(text.charAt(i))) {
                    firstNonWhitespaceIndex = i;
                    break;
                }
            }
            if (firstNonWhitespaceIndex > -1) {
                String newText = text.substring(0, firstNonWhitespaceIndex)
                        + Character.toUpperCase(text.charAt(firstNonWhitespaceIndex));
                if (firstNonWhitespaceIndex < text.length() - 1) {
                    newText += text.substring(firstNonWhitespaceIndex + 1);
                }
                return newText;
            }
        }
        return text;
    }

    private static boolean requiresCap(CharSequence leftContext) {
        if (leftContext != null) {
            for (int i = leftContext.length() - 1; i >= 0; i--) {
                char c = leftContext.charAt(i);
                if (Constants.CHARACTERS_EOS.contains(c)) {
                    return true;
                }
                if (!Constants.isTransparent(c)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Return a whitespace if
     * - the 1st character of the text is not punctuation, or whitespace, etc.
     * - or the previous character (last character of the left context) is not opening bracket, etc.
     */
    private static String getGlue(String text, CharSequence leftContext) {
        if (leftContext == null || text.isEmpty()) {
            return "";
        }
        char firstChar = text.charAt(0);

        // TODO: experimental: glue all 1-character strings (somewhat Estonian-specific)
        if (text.length() == 1 && Character.isLetter(firstChar)) {
            return "";
        }

        // Glue whitespace and punctuation
        if (leftContext.length() == 0
                || Character.isWhitespace(firstChar)
                || Constants.CHARACTERS_STICKY_LEFT.contains(firstChar)) {
            return "";
        }

        // Glue if the previous character is "sticky", e.g. opening bracket.
        char prevChar = leftContext.charAt(leftContext.length() - 1);
        if (Character.isWhitespace(prevChar)
                || Constants.CHARACTERS_STICKY_RIGHT.contains(prevChar)) {
            return "";
        }
        return " ";
    }
}
