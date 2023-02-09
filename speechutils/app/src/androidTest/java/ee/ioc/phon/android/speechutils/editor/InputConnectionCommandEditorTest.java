package ee.ioc.phon.android.speechutils.editor;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

// TODO: add tests for multiline input strings

@RunWith(AndroidJUnit4.class)
public class InputConnectionCommandEditorTest {
    private static final List<UtteranceRewriter> URS;

    static {
        // Simple replacements
        List<Command> list1 = new ArrayList<>();
        list1.add(new Command("DELETE ME", ""));
        list1.add(new Command("old_word", "new_word"));
        list1.add(new Command("dollar sign", "\\$"));
        list1.add(new Command("double backslash", "\\\\\\\\"));
        list1.add(new Command("hyphen", "-"));
        list1.add(new Command("space", " "));
        list1.add(new Command("newbullet", "\n- "));
        // TODO: are the following 2 supposed to be equivalent (did this change in Android 11?)
        list1.add(new Command("prefix1(optional)?", "$1"));
        list1.add(new Command("prefix2(optional|)", "$1"));
        list1.add(new Command("replace with selection", "A @sel() B"));

        // Editor commands
        List<Command> list2 = new ArrayList<>();
        list2.add(new Command("insert (.+)", "<>", "replace", new String[]{"<>", "$1"}));
        list2.add(new Command("double (.+)", "<> <>", "replaceAll", new String[]{"<>", "$1"})); // TODO: replaceAll is not available
        list2.add(new Command("s/(.*)/(.*)/", "", "replace", new String[]{"$1", "$2"}));
        list2.add(new Command("connect (.*) and (.*)", "", "replace", new String[]{"$1 $2", "$1-$2"}));
        list2.add(new Command("delete (.+)", "", "replace", new String[]{"$1"}));
        list2.add(new Command("delete2 (.*)", "D2", "replace", new String[]{"$1", ""}));
        list2.add(new Command("underscore (.*)", "", "replace", new String[]{"$1", "_$1_"}));
        list2.add(new Command("select (.*)", "", "select", new String[]{"$1"}));
        list2.add(new Command("selectAll", "", "selectAll"));
        list2.add(new Command("resetSel", "", "moveRel", new String[]{"0"}));
        // Add some text and then move to the beginning of the doc
        list2.add(new Command("(.*)\\bmoveAbs0", "$1", "moveAbs", new String[]{"0"}));
        list2.add(new Command("selection_replace (.*)", "", "replaceSel", new String[]{"$1"}));
        list2.add(new Command("selection_underscore", "", "replaceSel", new String[]{"_@sel()_"}));
        list2.add(new Command("replaceSelRe_noletters", "", "replaceSelRe", new String[]{"[a-z]", ""}));
        list2.add(new Command("replaceSelRe_underscore", "", "replaceSelRe", new String[]{"(.+)", "_\\$1_"}));
        //list2.add(new Command("replaceSelRe (.+?) .+ (.+)", "", "replaceSelRe", new String[]{"$1 (.+) $2", "$1 \\$1 $2"}));
        list2.add(new Command("replaceSelRe ([^ ]+) .+ ([^ ]+)", "", "replaceSelRe", new String[]{"$1 ([^ ]+) $2", "$1 \\$1 $2"}));
        list2.add(new Command("selection_quote", "", "replaceSel", new String[]{"\"@sel()\""}));
        list2.add(new Command("selection_double", "", "replaceSel", new String[]{"@sel()@sel()"}));
        // Hyphenates the current selection to the uttered number, adds brackets around the whole thing,
        // and selects the uttered number. Notice the need to escape the closing bracket and the end marking dollar sign.
        list2.add(new Command("selection_bracket ([0-9]+)", "", "replaceSel", new String[]{"(@sel()-$1)", "-([0-9]+)\\\\)\\$"}));
        list2.add(new Command("selection_inc", "", "incSel"));
        list2.add(new Command("selection_uc", "", "ucSel"));
        list2.add(new Command("step back", "", "moveRel", new String[]{"-1"}));
        list2.add(new Command("prev_sent", "", "selectReBefore", new String[]{"[.?!]()[^.?!]+[.?!][^.?!]+"}));
        list2.add(new Command("first_number", "", "selectReAfter", new String[]{"(\\d)\\."}));
        list2.add(new Command("second_number", "", "selectReAfter", new String[]{"(\\d)\\.", "2"}));
        list2.add(new Command("next_word", "", "selectReAfter", new String[]{"\\b(.+?)\\b"}));
        list2.add(new Command("next_next_word", "", "selectReAfter", new String[]{"\\b(.+?)\\b", "2"}));
        list2.add(new Command("prev_sel", "", "selectReBefore", new String[]{"@sel()"}));
        list2.add(new Command("next_sel", "", "selectReAfter", new String[]{"@sel()"}));
        list2.add(new Command("code (\\d+)", "", "keyCode", new String[]{"$1"}));
        list2.add(new Command("code letter (.)", "", "keyCodeStr", new String[]{"$1"}));
        list2.add(new Command("undo (\\d+)", "", "undo", new String[]{"$1"}));
        list2.add(new Command("combine (\\d+)", "", "combine", new String[]{"$1"}));
        list2.add(new Command("apply (\\d+)", "", "apply", new String[]{"$1"}));
        list2.add(new Command("timestamp", "", "replaceSel", new String[]{"@timestamp(G 'text', de)"}));

        // More simple replacements
        List<Command> list3 = new ArrayList<>();
        list3.add(new Command("connect word1 and word2", "THIS SHOULD NOT MATCH"));
        list3.add(new Command("(\\d+) times (\\d+)", "$1 * $2"));
        // Positive lookbehind and lookahead
        // Look-behind pattern matches must have a bounded maximum length
        // list.add(new Command("(?<=\\d+) times_ (?=\\d+)", " * "));
        list3.add(new Command("(?<=\\d{0,8}) times_ (?=\\d+)", " * "));

        URS = new ArrayList<>();
        URS.add(new UtteranceRewriter(Collections.unmodifiableList(list1)));
        URS.add(new UtteranceRewriter(Collections.unmodifiableList(list2)));
        URS.add(new UtteranceRewriter(Collections.unmodifiableList(list3)));
    }

    private InputConnectionCommandEditor mEditor;

    @Before
    public void before() {
        Context context = getInstrumentation().getContext();
        EditText view = new EditText(context);
        //view.setText("elas metsas mutionu, keset kuuski noori-vanu");
        EditorInfo editorInfo = new EditorInfo();
        //editorInfo.initialSelStart = 12;
        //editorInfo.initialSelEnd = 19;
        mEditor = new InputConnectionCommandEditor(context);
        // maybe: new BaseInputConnection(view, true);
        mEditor.setInputConnection(view.onCreateInputConnection(editorInfo));
        mEditor.setRewriters(URS);
    }

    @Test
    public void test01() {
        assertNotNull(mEditor.getInputConnection());
        assertTrue(Op.NO_OP.isNoOp());
    }

    @Test
    public void test02() {
        add("start12345 67890");
        assertThatTextIs("Start12345 67890");
        runOp(mEditor.deleteLeftWord());
        assertThatTextIs("Start12345");
        runOp(mEditor.replace("12345", ""));
        assertThatTextIs("Start");
    }

    @Test
    public void test03() {
        assertTrue(true);
    }

    @Test
    public void test04() {
        addPartial("...123");
        addPartial("...124");
        assertThatTextIs("...124");
        add("...1245");
        runOp(mEditor.moveAbs(4));
        assertThat(getTextBeforeCursor(10), is("...1"));
        add("-");
        assertThatTextIs("...1-245");
        add("undo 2", "-");
        assertThatTextIs("...1245-");
    }

    @Test
    public void test05() {
        add("a12345 67890_12345");
        runOp(mEditor.select("12345"));
        assertThat(getTextBeforeCursor(2), is("0_"));
        runOp(mEditor.deleteLeftWord());
        assertThatTextIs("A12345 67890_");
        runOp(mEditor.deleteLeftWord());
        assertThatTextIs("A12345");
    }

    @Test
    public void test06() {
        add("a12345 67890_12345");
        runOp(mEditor.replace("12345", "abcdef"));
        runOp(mEditor.replaceSel(" "));
        runOp(mEditor.replace("12345", "ABC"));
        assertThat(getTextBeforeCursor(2), is("BC"));
        runOp(mEditor.replaceSel("\n"));
        runOp(mEditor.replaceSel(" "));
        runOp(mEditor.moveAbs(9));
        assertThat(getTextBeforeCursor(2), is("67"));
    }

    @Test
    public void test07() {
        add("123456789");
        runOp(mEditor.moveAbs(2));
        assertThat(getTextBeforeCursor(2), is("12"));
        assertThatTextIs("123456789");
    }

    @Test
    public void test008() {
        add("double backslash");
        assertThatTextIs("\\\\");
    }

    @Test
    public void test009() {
        add("dollar sign");
        assertThatTextIs("$");
    }

    @Test
    public void test10() {
        add("test old_word test");
        assertThatTextIs("Test new_word test");
    }

    @Test
    public void test11() {
        add("test word1");
        add("s/word1/word2/");
        assertThatTextIs("Test word2");
    }

    @Test
    public void test12() {
        add("test word1 word2");
        add("connect word1 and word2");
        assertThatTextIs("Test word1-word2");
    }

    @Test
    public void test13() {
        add("test word1 word2");
        add("connect word1");
        add("and");
        assertThatUndoStackIs("[delete 4, delete 14, delete 16]");
        CommandEditorResult cer = mEditor.commitFinalResult("word2");
        assertTrue(cer.isSuccess());
        assertThat(cer.getRewrite().ppCommand(), is("replace (word1 word2) (word1-word2)"));
        assertThatUndoStackIs("[undo replace2, delete 16]");
        assertThatTextIs("Test word1-word2");
    }

    @Test
    public void test14() {
        add("test word1");
        runOp(mEditor.replaceSel(" "));
        add("word2");
        assertThatTextIs("Test word1 word2");
        add("connect word1 and word2");
        assertThatTextIs("Test word1-word2");
    }

    @Test
    public void test15() {
        add("test word1");
        runOp(mEditor.replaceSel(" "));
        add("word2");
        assertThat(getTextBeforeCursor(11), is("word1 word2"));
        runOp(mEditor.deleteAll());
        assertThat(getTextBeforeCursor(1), is(""));
    }

    /**
     * If command does not fully match then its replacement is ignored.
     */
    @Test
    public void test16() {
        add("I will delete something");
        assertThatTextIs("I will delete something");
    }

    @Test
    public void test17() {
        add("there are word1 and word2...");
        add("select word1 and word2");
        runOp(mEditor.moveAbs(-100));
        assertThatTextIs("There are word1 and word2...");
    }

    @Test
    public void test18() {
        add("there are word1 and word2...", "select word1 and word2", "selection_replace REPL");
        assertThatTextIs("There are REPL...");
    }

    @Test
    public void test19() {
        add("there are word1 and word2...", "select word1 and word2", "selection_underscore");
        assertThatTextIs("There are _word1 and word2_...");
        undo();
        assertThatTextIs("There are word1 and word2...");
    }

    @Test
    public void test20() {
        add("a", "select a", "selection_double", "selection_double");
        runOp(mEditor.moveAbs(-1));
        assertThat(getTextBeforeCursor(5), is("AA"));
    }

    @Test
    public void test21() {
        add("123456789", "select 3", "selection_inc");
        runOp(mEditor.moveRel(3));
        add("select 5", "selection_inc");
        assertThatTextIs("124466789");
    }

    @Test
    public void test22() {
        add("this is some word");
        add("select is some");
        add("selection_uc");
        assertThatTextIs("This IS SOME word");
    }

    @Test
    public void test23() {
        add("this is some word");
        runOp(mEditor.selectAll());
        add("selection_replace REPL");
        assertThatTextIs("REPL");
    }

    @Test
    public void test24() {
        add("test word1 word2");
        add("connect word1 and not_exist");
        assertThatTextIs("Test word1 word2");
    }

    @Test
    public void test25() {
        add("test word1 word2");
        undo();
        assertThatTextIs("");
    }

    @Test
    public void test26() {
        add("1234567890");
        add("step back");
        runOp(mEditor.moveRel(-1));
        undo();
        runOp(mEditor.deleteLeftWord());
        assertThatTextIs("0");
        undo();
        assertThatTextIs("1234567890");
    }

    // test27

    @Test
    public void test28() {
        add("1234567890");
        runOp(mEditor.moveRel(-5));
        runOp(mEditor.moveRel(2));
        undo(2);
        runOp(mEditor.moveRel(-1));
        runOp(mEditor.deleteLeftWord());
        undo();
        assertThatTextIs("1234567890");
    }

    /**
     * old_word is rewritten into new_word and then changed using a command to NEWER_WORD
     */
    @Test
    public void test29() {
        add("test old_word");
        assertThatTextIs("Test new_word");
        add("s/new_word/NEWER_WORD/");
        assertThatUndoStackIs("[undo replace2, delete 13]");
        assertThatTextIs("Test NEWER_WORD");
        undo();
        assertThatUndoStackIs("[delete 13]");
        assertThatTextIs("Test new_word");
        undo();
        assertThatTextIs("");
    }

    @Test
    public void test30() {
        runOp(mEditor.replaceSel(" "));
        assertThatTextIs(" ");
        undo();
        assertThatTextIs("");
    }

    @Test
    public void test30a() {
        runOp(mEditor.replaceSel(null));
        assertThatTextIs("");
        undo();
        assertThatTextIs("");
        runOp(mEditor.replaceSel(""));
        assertThatTextIs("");
        undo();
        assertThatTextIs("");
    }

    @Test
    public void test31() {
        add("there are word1 and word2...", "select word1 and word2", "selection_replace REPL");
        assertThatTextIs("There are REPL...");
        assertThatUndoStackIs("[deleteSurroundingText+commitText, setSelection, delete 28]");
        undo();
        assertThatTextIs("There are word1 and word2...");
    }

    /**
     * Failed command must not change the editor content.
     */
    @Test
    public void test32() {
        add("there are word1 and word2...");
        add("select nonexisting_word");
        undo();
        assertThatTextIs("");
    }

    /**
     * Failed command must not change the editor content.
     */
    @Test
    public void test33() {
        add("this is a text");
        add("this is another text");
        add("select nonexisting_word");
        undo();
        assertThatTextIs("This is a text");
    }

    /**
     * Apply a command with a non-empty rewrite and undo it.
     * First the command is undone, then the rewrite.
     */
    @Test
    public void test34() {
        add("this_is_a_text");
        add("delete2 is_a");
        assertThatTextIs("This__text D2");
        assertThatUndoStackIs("[undo replace1, delete 3, delete 14]");
        undo();
        assertThatTextIs("This_is_a_text D2");
        undo();
        assertThatTextIs("This_is_a_text");
    }

    /**
     * Undo a multi-segment command that succeeds.
     */
    @Test
    public void test35() {
        add("test word1 word2");
        add("connect word1");
        add("and");
        CommandEditorResult cer = mEditor.commitFinalResult("word2");
        assertTrue(cer.isSuccess());
        assertThat(cer.getRewrite().ppCommand(), is("replace (word1 word2) (word1-word2)"));
        assertThatUndoStackIs("[undo replace2, delete 16]");
        assertThatTextIs("Test word1-word2");
        undo();
        assertThatTextIs("Test word1 word2");
    }

    /**
     * Undo a multi-segment command that fails.
     */
    @Test
    public void test36() {
        add("test word1 word2");
        add("connect word1");
        add("and");
        CommandEditorResult cer = mEditor.commitFinalResult("nonexisting_word");
        assertFalse(cer.isSuccess());
        assertThat(cer.getRewrite().ppCommand(), is("replace (word1 nonexisting_word) (word1-nonexisting_word)"));
        assertThatUndoStackIs("[delete 16]");
        assertThatTextIs("Test word1 word2");
        undo();
        assertThatTextIs("");
    }

    /**
     * Dictating over a selection
     */
    @Test
    public void test37() {
        add("this is a text");
        add("select is a");
        add("is not a");
        assertThatTextIs("This is not a text");
    }

    @Test
    public void test38() {
        add("this is a text");
        add("select is a");
        add("selection_replace is not a");
        assertThatTextIs("This is not a text");
    }

    @Test
    public void test39() {
        add("this is a text");
        add("select is a");
        add("selection_replace is not a");
        assertThatUndoStackIs("[deleteSurroundingText+commitText, setSelection, delete 14]");
        assertThatTextIs("This is not a text");
        undo();
        assertThatTextIs("This is a text");
    }

    @Test
    public void test40() {
        add("this is a text");
        add("select is a");
        add("selection_replace");
        assertThatUndoStackIs("[delete 17, setSelection, delete 14]");
        assertThatTextIs("This selection_replace text");
        add("is not a");
        assertThatUndoStackIs("[deleteSurroundingText+commitText, setSelection, delete 14]");
        assertThatTextIs("This is not a text");
        undo();
        assertThatUndoStackIs("[setSelection, delete 14]");
        assertThatTextIs("This is a text");
        undo();
        assertThatTextIs("This is a text");
        undo();
        assertThatTextIs("");
    }

    /**
     * deleteLeftWord deletes the selection
     */
    @Test
    public void test41() {
        add("1234567890");
        add("select 456");
        runOp(mEditor.deleteLeftWord());
        assertThatTextIs("1237890");
        undo();
        assertThatTextIs("1234567890");
    }


    @Test
    public void test42() {
        add("test word1 word2");
        addPartial("connect word1 and word2");
        add("connect word1 and word2");
        assertThatTextIs("Test word1-word2");
        undo();
        assertThatTextIs("Test word1 word2");
    }

    @Test
    public void test42a() {
        add("test word1 word2");
        addPartial("connect word1 and word2");
        assertThatTextIs("Test word1 word2");
        runOp(mEditor.replace("word1 word2", "word1-word2"));
        assertThatTextIs("Test word1-word2");
    }

    /**
     * An existing selection should not matter if the command is not about selection
     */
    @Test
    public void test43() {
        add("test word1 word2 word3");
        add("select word3");
        assertThatTextIs("Test word1 word2 word3");
        // Returns false if there is a selection
        assertFalse(mEditor.commitPartialResult("connect word1 and word2"));
        add("connect word1 and word2");
        assertThatTextIs("Test word1-word2 word3");
        undo();
        assertThatTextIs("Test word1 word2 word3");
    }

    /**
     * Partial results should not have an effect on the command.
     */
    @Test
    public void test44() {
        add("test word1", ".");
        addPartial("s/word1");
        addPartial("s/word1/word2/");
        add("s/word1/word2/");
        assertThatTextIs("Test word2.");
    }

    @Test
    public void test45() {
        add("sentence", ".");
        add("sentence");
        assertThatTextIs("Sentence. Sentence");
    }

    @Test
    public void test46() {
        add("Sentence", ".");
        addPartial("DELETE");
        assertThatTextIs("Sentence. DELETE");
        add("DELETE ME");
        assertThatTextIs("Sentence.");
    }

    /**
     * Auto-capitalization
     */
    @Test
    public void test47() {
        addPartial("this is 1st test.");
        add("this is 1st test. this is 2nd test.");
        addPartial("this is 3rd");
        add("this is 3rd test.");
        // TODO: 2nd sentence is lowercase, right?
        assertThatTextIs("This is 1st test. this is 2nd test. This is 3rd test.");
        add("delete this");
        assertThatTextIs("This is 1st test. this is 2nd test.  is 3rd test.");
        undo();
        // TODO: capitalization is not restored
        assertThatTextIs("This is 1st test. this is 2nd test. This is 3rd test.");
    }

    /**
     * Undoing final texts
     */
    @Test
    public void test48() {
        addPartial("this is 1st test.");
        add("this is 1st test. This is 2nd test.");
        addPartial("this is 3rd");
        add("this is 3rd test.");
        assertThatTextIs("This is 1st test. This is 2nd test. This is 3rd test.");
        undo();
        assertThatTextIs("This is 1st test. This is 2nd test.");
        undo();
        assertThatTextIs("");
    }

    /**
     * Regex based selection.
     */
    @Test
    public void test49() {
        add("This is number 1. This is number 2.");
        runOp(mEditor.selectReBefore("number "));
        add("#");
        assertThatTextIs("This is number 1. This is #2.");
    }

    /**
     * Regex based selection using capturing groups.
     */
    @Test
    public void test50() {
        add("This is number 1. This is number 2.");
        runOp(mEditor.selectReBefore("(\\d+)\\."));
        add("II");
        assertThatTextIs("This is number 1. This is number II.");
    }

    /**
     * Regex based selection using an empty capturing group.
     */
    @Test
    public void test51() {
        add("This is number 1. This is number 2? This is", "prev_sent");
        add("yes,");
        assertThatTextIs("This is number 1. Yes, This is number 2? This is");
        add("undo 2");
        add("3");
        assertThatTextIs("This is number 1. This is number 2? This is 3");
    }

    // test52, test53

    /**
     * Apply a command multiple times.
     */
    @Test
    public void test54() {
        add("6543210", "step back", "apply 4", "-");
        assertThatTextIs("65-43210");
        assertThatUndoStackIs("[delete 1, undo apply 4, setSelection, delete 7]");
        undo();
        assertThatTextIs("6543210");
        undo();
        assertThatUndoStackIs("[setSelection, delete 7]");
        add("-");
        assertThatTextIs("654321-0");
    }

    /**
     * Delete a string multiple times.
     */
    @Test
    public void test55() {
        add("6 5 4 3 2 1 0", "delete  ", "apply 4", "-");
        assertThatTextIs("6 5-43210");
        add("undo 1");
        assertThatTextIs("6 543210");
        add("undo 1");
        assertThatTextIs("6 5 4 3 2 10");
    }

    /**
     * Combine last 3 commands and apply the result 2 times.
     */
    @Test
    public void test56() {
        add("0 a a a a b a", "select a", "selection_uc", "step back");
        assertThatTextIs("0 a a a a b A");
        add("-");
        assertThatTextIs("0 a a a a b -A");
        undo();
        assertThatTextIs("0 a a a a b A");
        assertThatOpStackIs("[moveRel, ucSel, select a]");
        add("combine 3");
        assertThatTextIs("0 a a a a b A");
        assertThatOpStackIs("[[select a, ucSel, moveRel] 3]");
        add("apply 2");
        assertThatTextIs("0 a a A A b A");
    }

    /**
     * Search for a string multiple times (i.e. apply "select" multiple times).
     * Change the 5th space with a hyphen.
     */
    @Test
    public void test57() {
        add("6 5 4 3 2 1 0", "select  ", "apply 4", "-");
        assertThatTextIs("6 5-4 3 2 1 0");
        undo();
        assertThatTextIs("6 5 4 3 2 1 0");
        undo();
        assertThatTextIs("6 5 4 3 2 1 0");
        add("-");
        assertThatTextIs("6 5 4 3 2 1-0");
    }

    @Test
    public void test60() {
        add("there are word1 and word2...");
        add("select word1 and word2");
        add("selection_uc");
        assertThatTextIs("There are WORD1 AND WORD2...");
        runOp(mEditor.moveAbs(-1));
        add("select word1 and word2");
        add("selection_quote");
        assertThatTextIs("There are \"WORD1 AND WORD2\"...");
    }

    /**
     * TODO: incorrectly replaces with "_some_" instead of "_SOME_"
     */
    @Test
    public void test61() {
        add("this is SOME word");
        add("underscore some");
        assertThatTextIs("This is _SOME_ word");
    }

    /**
     * Same as before but using selection.
     */
    @Test
    public void test62() {
        add("this is SOME word");
        add("select some");
        add("selection_underscore");
        assertThatTextIs("This is _SOME_ word");
    }

    // test63

    /**
     * Undoing a move restores the selection.
     */
    @Test
    public void test64() {
        add("123 456 789");
        add("select 456");
        add("step back", "step back");
        undo(2);
        add("selection_underscore");
        assertThatTextIs("123 _456_ 789");
    }

    /**
     * Repeat the last utterance twice.
     */
    @Test
    public void test65() {
        runOpFromText("123");
        runOpFromText("apply 2");
        assertThatTextIs("123 123 123");
    }

    /**
     * Combine last 2 commands and apply the result 2 times.
     */
    @Test
    public void test66() {
        add("0 a _ a _ a _", "s/a/b/", "s/_/*/");
        assertThatTextIs("0 a _ a * b _");
        add("combine 2");
        add("apply 2");
        assertThatTextIs("0 b * b * b _");
    }

    /**
     * Perform regex search that fails.
     */
    @Test
    public void test67() {
        add("Test 1.");
        runOpThatFails(mEditor.selectReBefore("(\\d{2})\\."));
        add("more");
        assertThatTextIs("Test 1. More");
    }

    /**
     * Perform undo that fails.
     */
    @Test
    public void test68() {
        addPartial("Initial text");
        assertThatOpStackIs("[]");
        assertThatUndoStackIs("[]");
        runOpThatFails(mEditor.undo(1));
        assertThatOpStackIs("[]");
        assertThatUndoStackIs("[]");
        assertThatTextIs("Initial text");
    }

    /**
     * Partial results are deleted, if followed by a command.
     */
    @Test
    public void test69() {
        addPartial("Initial text");
        assertThatTextIs("Initial text");
        assertThatOpStackIs("[]");
        assertThatUndoStackIs("[]");
        add("select whatever");
        assertThatOpStackIs("[]");
        assertThatUndoStackIs("[]");
        assertThatTextIs("");
    }

    /**
     * Search after the cursor, select the 1st match, and replace it.
     */
    @Test
    public void test70() {
        add("This is number 1. This is number 2. This is number 3.");
        runOp(mEditor.moveAbs(0));
        //CommandEditorManager.EditorCommand ec = CommandEditorManager.EDITOR_COMMANDS.get(CommandEditorManager.SELECT_RE_AFTER);
        //runOp(ec.getOp(mEditor, new String[]{"(\\d)\\."}));
        runOp(mEditor.selectReAfter("(\\d)\\.", 1));
        // TODO: fails currently
        //add("first_number");
        add("I");
        assertThatTextIs("This is number I. This is number 2. This is number 3.");
    }

    /**
     * Search after the cursor, select the 2nd match, and replace it.
     */
    @Test
    public void test71() {
        add("This is number 1. This is number 2. This is number 3.");
        runOp(mEditor.moveAbs(0));
        runOp(mEditor.selectReAfter("(\\d)\\.", 2));
        // TODO: fails currently
        //add("second_number");
        add("II");
        assertThatTextIs("This is number 1. This is number II. This is number 3.");
    }

    @Test
    public void test72() {
        add("selectAll", "123 456", "selectAll", "resetSel");
        add("selection_replace !");
        assertThatTextIs("123 456!");
    }

    @Test
    public void test73() {
        add("123 456", "selectAll");
        add("selection_replace !");
        assertThatTextIs("!");
        undo(1);
        add("selection_replace !");
        assertThatTextIs("!");
    }

    @Test
    public void test74() {
        add("123 456", "step back", "selection_replace _");
        assertThatTextIs("123 45_6");
        add("undo 1");
        assertThatTextIs("123 456");
        add("undo 1", "selection_replace _");
        assertThatTextIs("123 456_");
    }

    @Test
    public void test75() {
        Collection<Op> collection = new ArrayList<>();
        collection.add(mEditor.moveRel(-1));
        collection.add(mEditor.select("2"));
        collection.add(mEditor.replaceSel("_"));
        add("123 4562");
        runOp(mEditor.combineOps(collection));
        assertThatTextIs("1_3 4562");
        add("undo 1");
        assertThatTextIs("123 4562");
        add("-");
        assertThatTextIs("123 4562-");
    }

    @Test
    public void test76() {
        Collection<Op> collection = new ArrayList<>();
        collection.add(mEditor.moveRel(-1));
        collection.add(mEditor.select("2"));
        collection.add(mEditor.replaceSel("_"));
        add("123 123 12");
        for (Op op : collection) {
            runOp(op);
        }
        assertThatTextIs("123 1_3 12");
        runOp(mEditor.combine(3));
        add("apply 1");
        assertThatOpStackIs("[apply, [moveRel, select 2, replaceSel] 3]");
        assertThatTextIs("1_3 1_3 12");
    }

    @Test
    public void test77() {
        Collection<Op> collection = new ArrayList<>();
        collection.add(mEditor.moveRel(-1));
        collection.add(mEditor.select(" "));
        collection.add(mEditor.replaceSel("-"));
        collection.add(mEditor.select("2"));
        collection.add(mEditor.replaceSel("_"));
        add("123 4562");
        Op op = mEditor.combineOps(collection);
        Op undo = op.run();
        assertThatTextIs("1_3-4562");
        Op undo1 = undo.run();
        assertThatTextIs("123 4562");
        undo1.run();
        assertThatTextIs("1_3-4562");
    }

    @Test
    public void test78() {
        runOpFromText("123");
        assertThatTextIs("123");
        runOpFromText("undo 1");
        assertThatTextIs("");
        runOpFromText("123");
        runOpFromText("select 2");
        runOpFromText("selection_replace _");
        assertThatTextIs("1_3");
        runOpFromText("undo 1");
        assertThatTextIs("123");
    }

    /**
     * Failed command is not added to the undo stack.
     */
    @Test
    public void test79() {
        runOpFromText("123");
        runOpFromText("456");
        assertThatTextIs("123 456");
        assertThatOpStackIs("[add 456, add 123]");
        assertThatUndoStackIs("[delete 4, delete 3]");
        runOpFromText("select 7");
        // Here stacks should remain the same because "select 7" fails.
        assertThatOpStackIs("[add 456, add 123]");
        assertThatUndoStackIs("[delete 4, delete 3]");
        runOpFromText("undo 1");
        assertThatTextIs("123");
    }

    /**
     * Calling selectReAfter N times vs passing N as the 2nd argument.
     * Should be equivalent.
     */
    @Test
    public void test80() {
        add("0 word1 word2 word3 word4 word5 word6");
        runOp(mEditor.moveAbs(1));
        runOp(mEditor.selectReAfter("\\b(.+?)\\b", 1));
        runOp(mEditor.replaceSel("[]"));
        assertThatTextIs("0 [] word2 word3 word4 word5 word6");
        runOp(mEditor.selectReAfter("\\b(.+?)\\b", 1));
        runOp(mEditor.selectReAfter("\\b(.+?)\\b", 1));
        runOp(mEditor.replaceSel("[]"));
        assertThatTextIs("0 [] word2 [] word4 word5 word6");
        runOp(mEditor.selectReAfter("\\b(.+?)\\b", 2));
        runOp(mEditor.replaceSel("[]"));
        // TODO: this currently fails
        assertThatTextIs("0 [] word2 [] word4 [] word6");
    }

    @Test
    public void test82() {
        add("Test 1.");
        runOp(mEditor.selectReBefore("^"));
        add("more");
        assertThatTextIs("MoreTest 1.");
    }

    @Test
    public void test83() {
        add("Test 1.");
        runOp(mEditor.selectReBefore("$"));
        add("more");
        assertThatTextIs("Test 1. More");
    }

    /**
     * Go to the beginning of the current sentence.
     */
    @Test
    public void test84() {
        add("Sent 1? Sent, 2.");
        //runOp(mEditor.selectReBefore("(?:^|[.?!]\\s+)()[^.?!]*[.?!]*"));
        runOp(mEditor.selectReBefore("(?:^|[.?!]\\s+)()"));
        add("more");
        assertThatTextIs("Sent 1? MoreSent, 2.");
    }

    /**
     * Go to the beginning of the current sentence.
     */
    @Test
    public void test85() {
        add("Sent 1!");
        runOp(mEditor.selectReBefore("(?:^|[.?!]\\s+)()"));
        add("more");
        assertThatTextIs("MoreSent 1!");
    }

    /**
     * Go to the end of the current sentence.
     */
    @Test
    public void test86() {
        add("Sent 1? Sent, 2.");
        runOp(mEditor.moveAbs(0));
        runOp(mEditor.selectReAfter("(?:$|[.?!]\\s+)()", 1));
        add("more");
        assertThatTextIs("Sent 1? MoreSent, 2.");
    }

    /**
     * Go to the end of the next sentence.
     */
    @Test
    public void test87() {
        add("Sent 1? Sent, 2.");
        runOp(mEditor.moveAbs(0));
        runOp(mEditor.selectReAfter("(?:$|[.?!]\\s+)()", 2));
        add("more");
        assertThatTextIs("Sent 1? Sent, 2. More");
    }

    @Test
    public void test88() {
        add("123456789");
        runOp(mEditor.moveAbs(0));
        runOp(mEditor.selectReAfter("(.)|(\\d)", 5));
        add("_");
        assertThatTextIs("1234 _6789");
    }

    @Test
    public void test89() {
        add("1234");
        runOp(mEditor.selectAll());
        runOp(mEditor.replaceSelRe("(2)(3)", "$2$1"));
        assertThatTextIs("1324");
    }

    @Test
    public void test90() {
        add("there are word1 and word2...", "select word1 and word2", "replaceSelRe_noletters");
        assertThatTextIs("There are 1  2...");
        undo();
        assertThatTextIs("There are word1 and word2...");
    }

    /**
     * Put underscores around the selection.
     */
    @Test
    public void test91() {
        add("there are word1 and word2...", "select word1 and word2", "replaceSelRe_underscore");
        assertThatTextIs("There are _word1 and word2_...");
        undo();
        assertThatTextIs("There are word1 and word2...");
    }

    /**
     * Replace middle words in the match.
     * TODO: fails
     */
    @Test
    public void test92() {
        add("there are word1 and word2 word3...", "select are word1 and word2");
        assertThatTextIs("There are word1 and word2 word3...");
        add("replaceSelRe word1 whatever1 whatever2 word2");
        assertThatTextIs("There are word1 whatever1 whatever2 word2 word3...");
    }

    @Test
    public void test93() {
        add("insert 1");
        assertThatTextIs("1");
    }

    @Test
    public void test95() {
        add("123 456", "select 123", "resetSel");
        add("selection_replace !");
        assertThatTextIs("123! 456");
    }

    @Test
    public void test96() {
        add("010010001", "select 1", "select @sel()", "select @sel()");
        add("selection_replace !");
        assertThatTextIs("0!0010001");
    }

    /**
     * "times" is replaced by "*" but only in the context of numbers.
     * Unwanted implementation: consumes numbers, thus not all "times" are replaced.
     */
    @Test
    public void test97() {
        add("times 1 times 2 times 3 times 4 times");
        assertThatTextIs("Times 1 * 2 times 3 * 4 times");
    }

    /**
     * "times" is replaced by "*" but only in the context of numbers.
     * Implementation with lookaround.
     */
    @Test
    public void test98() {
        add("times_ 1 times_ 2 times_ 3 times_ 4 times_");
        assertThatTextIs("Times_ 1 * 2 * 3 * 4 times_");
    }

    /**
     * Adds some text and then moves to the beginning of doc.
     * TODO: not sure if the trailing space should be allowed.
     */
    @Test
    public void test99() {
        add("123 moveAbs0", "456");
        assertThatTextIs("456123 ");
    }

    @Test
    public void test100() {
        add("123", "456 moveAbs0");
        assertThatTextIs("123 456 ");
        add("789");
        assertThatTextIs("789123 456 ");
    }

    /**
     * Because below there is always a selection before deleteChars is applied,
     * it ignores its args and just deletes the selection.
     */
    @Test
    public void test101() {
        add("1234567890");
        runOp(mEditor.moveRel(-5));
        runOp(mEditor.moveRelSel(2, 1));
        runOp(mEditor.deleteChars(100));
        assertThatTextIs("12345890");
        runOp(mEditor.moveRelSel(-2, 0));
        runOp(mEditor.deleteChars(1));
        assertThatTextIs("123890");
        // Cursor ends cross
        runOp(mEditor.moveRelSel(-2, 1));
        runOp(mEditor.deleteChars(2));
        assertThatTextIs("1890");
        runOp(mEditor.moveRelSel(2, 1));
        runOp(mEditor.moveRelSel(1, 0));
        runOp(mEditor.deleteChars(1));
        assertThatTextIs("180");
        undo(9);
        assertThatTextIs("1234567890");
    }

    @Test
    public void test102() {
        add("123 456 789");
        runOp(mEditor.moveAbs(5));
        runOp(mEditor.selectRe("\\d+", false));
        add("new");
        assertThatTextIs("123 new 789");
    }

    /**
     * Deleting 😃 can be done by deleting a single char.
     */
    @Test
    public void test103() {
        add("\uD83D\uDE03");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runOp(mEditor.deleteChars(-1));
        } else {
            runOp(mEditor.deleteChars(-2));
        }
        assertThatTextIs("");
    }

    @Test
    public void test104() {
        String initial = "123456789";
        add(initial);
        runOp(mEditor.moveRel(-5));
        runOp(mEditor.deleteChars(-2));
        runOp(mEditor.deleteChars(3));
        assertThatTextIs("1289");
        runOp(mEditor.undo(2));
        assertThatTextIs(initial);
    }

    @Test
    public void test105() {
        String str = "Word1 word2";
        add(str);
        runOp(mEditor.deleteLeftWord());
        assertThatTextIs("Word1");
        undo();
        assertThatTextIs(str);
        runOp(mEditor.deleteLeftWord());
        assertThatTextIs("Word1");
    }

    @Test
    public void test106() {
        add("1 2 3 1 2 3 1 2 3");
        runOp(mEditor.selectReBefore("2"));
        add("apply 2");
        add("next_sel");
        add("*");
        assertThatTextIs("1 2 3 1 * 3 1 2 3");
        runOp(mEditor.selectReAfter("2", 1));
        add("prev_sel");
        add("*");
        assertThatTextIs("1 * 3 1 * 3 1 2 3");
        undo(2);
        add("*");
        assertThatTextIs("1 2 3 1 * 3 1 * 3");
        runOp(mEditor.selectReBefore("1"));
        add("apply 2");
        add("next_sel");
        add("*");
        assertThatTextIs("1 2 3 * * 3 1 * 3");
        undo(3);
        add("*");
        assertThatTextIs("1 2 3 1 * 3 * * 3");
    }

    @Test
    public void test107() {
        add("test prefix1optional");
        assertThatTextIs("Test optional");
    }

    // "(opt)?" is equal to "(opt|)"
    // In Android 10, the first returns null if opt is missing.
    @Test
    public void test108() {
        add("test prefix1suffix");
        // assertThatTextIs("Test nullsuffix"); // Android 10
        assertThatTextIs("Test suffix"); // Android 11
    }

    @Test
    public void test109() {
        add("test prefix2prefix2suffix");
        assertThatTextIs("Test suffix");
    }

    /**
     * Replace with selection if nothing has been selected.
     * TODO: do we want this, i.e. may it would make sense if replacement resolved @sel()
     */
    @Test
    public void test110() {
        add("replace with selection");
        assertThatTextIs("A @sel() B");
    }

    /**
     * Replace with selection if something has been selected.
     * TODO: do we want this, i.e. may it would make sense if replacement resolved @sel()
     */
    @Test
    public void test111() {
        add("123456");
        runOp(mEditor.select("123"));
        add("replace with selection");
        assertThatTextIs("A @sel() B456");
    }

    // Can't create handler inside thread that has not called Looper.prepare()
    //@Test
    public void test201() {
        runOp(mEditor.copy());
        runOp(mEditor.paste());
        runOp(mEditor.paste());
    }

    //@Test
    public void test202() {
        add("1234567890");
        runOp(mEditor.keyLeft());
        runOp(mEditor.keyLeft());
        undo();
        runOp(mEditor.deleteLeftWord());
        assertThatTextIs("0");
    }

    /**
     * Numeric keycode.
     * TODO: Works in the app but not in the test.
     */
    //@Test
    public void test203() {
        add("This is a test", "code 66");
        runOp(mEditor.keyCode(66));
        runOp(mEditor.keyCodeStr("A"));
        assertThatTextIs("This is a testA");
    }

    /**
     * Symbolic keycode
     * TODO: Works in the app but not in the test.
     */
    //@Test
    public void test204() {
        add("This is a test", "code letter B");
        assertThatTextIs("This is a testB");
    }

    /**
     * TODO: Can't create handler inside thread that has not called Looper.prepare()
     */
    //@Test
    public void test205() {
        add("test word1");
        runOp(mEditor.replaceSel(" "));
        add("word2");
        assertThatTextIs("Test word1 word2");
        runOp(mEditor.cutAll());
        assertThat(getTextBeforeCursor(1), is(""));
        runOp(mEditor.paste());
        assertThatTextIs("Test word1 word2");
    }

    /**
     * Single letter is always glued to the previous symbol, but possibly capitalized.
     */
    @Test
    public void test206() {
        add("test.");
        add("a");
        assertThatTextIs("Test.A");
    }

    @Test
    public void test207() {
        add("test!");
        add("  ");
        add("abc");
        assertThatTextIs("Test!  Abc");
    }

    /**
     * Spacing around hyphen.
     * TODO: do not capitalize after " - "
     */
    @Test
    public void test208() {
        add("test", "-", "test", "hyphen", "test", "space", "hyphen", "space", "test", "newbullet", "test");
        assertThatTextIs("Test-test-test - Test\n- test");
    }

    /**
     * Spacing around common punctuation.
     */
    @Test
    public void test209() {
        add("test", "test", ",", "test", ":", "test", "!", "test", "(", "test", "test", ")", ".", "(", "test", ".", ")");
        assertThatTextIs("Test test, test: test! Test (Test test). (Test.)");
    }

    /**
     * Spacing around common punctuation.
     * Capitalization depends on how much left context is available.
     * If left context contains only transparent characters then the text is capitalized.
     */
    @Test
    public void test210() {
        add("test (", "test", "!", "test", "(", "test");
        assertThatTextIs("Test (test! Test (Test");
    }

    // TODO: make AsyncTask ops testable
    // @Test
    public void test211() {
        add("123 456", "select 123");
        runOp(mEditor.getUrl("http://api.mathjs.org/v4/?expr=2-3", null));
        assertThatTextIs("-1 456");
    }

    /**
     * selectReBefore interprets the selection as a plain string (not as a regex
     */
    @Test
    public void test212() {
        add(". 2 .");
        runOp(mEditor.selectReBefore("\\."));
        runOp(mEditor.selectReBefore("@sel()"));
        add("1");
        assertThatTextIs("1 2 .");
    }

    /**
     * selectReAfter interprets the selection as a plain string (not as a regex
     */
    @Test
    public void test213() {
        add(". 2 .");
        runOp(mEditor.selectReBefore("\\."));
        runOp(mEditor.selectReBefore("\\."));
        runOp(mEditor.selectReAfter("@sel()", 1));
        add("3");
        assertThatTextIs(". 2 3");
    }

    /**
     * replaceSel + setting the cursor position (denoted by [] below).
     * test[]
     * test<[abc]>
     * BEFORE: t<
     * test<123[]>
     */
    @Test
    public void test214() {
        add("test");
        runOp(mEditor.replaceSel("<abc>", "^.(...)"));
        assertThat(getTextBeforeCursor(2), is("t<"));
        add("123");
        assertThat(getTextBeforeCursor(2), is("23"));
    }

    @Test
    public void test215() {
        add("test");
        runOp(mEditor.replaceSel("<>", "^.()"));
        assertThat(getTextBeforeCursor(2), is("t<"));
        add("123");
        assertThat(getTextBeforeCursor(2), is("23"));
    }

    @Test
    public void test216() {
        runOp(mEditor.replaceSel("Hi <NAME>,", "<NAME>"));
        assertThat(getTextBeforeCursor(3), is("Hi "));
        add("World");
        assertThatTextIs("Hi World,");
    }

    @Test
    public void test217() {
        runOp(mEditor.replaceSel("Hi <NAME>,", "<NOMATCH>"));
        assertThat(getTextBeforeCursor(3), is("E>,"));
        add("World");
        assertThatTextIs("Hi <NAME>, World");
    }

    /**
     * TODO: fix undo of replaceSel/2
     */
    @Test
    public void test218() {
        runOp(mEditor.replaceSel("Hi <NAME>,", "<NAME>"));
        undo();
        add("World");
        assertThatTextIs("World");
    }

    @Test
    public void test219() {
        add("0123");
        runOp(mEditor.select("123"));
        runOp(mEditor.replaceSel("<@sel()>", ".(.*)."));
        assertThatTextIs("0<123>");
        add("456");
        assertThatTextIs("0<456>");
    }

    @Test
    public void test220() {
        add("1", "select 1", "selection_bracket 234");
        assertThatTextIs("(1-234)");
        assertThat(getTextBeforeCursor(1), is("-"));
        assertThat(getTextAfterCursor(1), is(")"));
    }

    /**
     * TODO: improve this test, so that it's stable across time and space
     */
    @Test
    public void test221() {
        add("timestamp");
        assertThatTextIs("n. Chr. text");
    }

    private String getTextBeforeCursor(int n) {
        return mEditor.getInputConnection().getTextBeforeCursor(n, 0).toString();
    }

    private String getTextAfterCursor(int n) {
        return mEditor.getInputConnection().getTextAfterCursor(n, 0).toString();
    }

    private void addPartial(String... texts) {
        for (String text : texts) {
            assertTrue(mEditor.commitPartialResult(text));
        }
    }

    private void add(String... texts) {
        for (String text : texts) {
            assertNotNull(mEditor.commitFinalResult(text));
        }
    }

    private void undo() {
        undo(1);
    }

    private void undo(int steps) {
        runOp(mEditor.undo(steps));
    }

    private void assertThatOpStackIs(String str) {
        assertThat(mEditor.getOpStack().toString(), is(str));
    }

    private void assertThatUndoStackIs(String str) {
        assertThat(mEditor.getUndoStack().toString(), is(str));
    }

    private void assertThatTextIs(String str) {
        assertThat(mEditor.getText().toString(), is(str));
    }

    private void runOp(Op op) {
        assertNotNull(op);
        assertTrue(mEditor.runOp(op));
        // TODO: we could check for each op if undo works as expected
        // do + undo
        //assertNotNull(op.run().run());
        // do
        //assertNotNull(op.run());
    }

    private void runOpThatFails(Op op) {
        assertNotNull(op);
        assertFalse(mEditor.runOp(op));
    }

    private void runOpFromText(String text) {
        Op op = mEditor.getOpOrNull(text, true);
        assertNotNull(op);
        // Op can fail
        mEditor.runOp(op);
    }
}