package ee.ioc.phon.android.speechutils.editor;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class RuleManagerTest {

    private static final Pattern UTTERANCE = Pattern.compile("", Constants.REWRITE_PATTERN_FLAGS);
    private static final String COMMENT = "";

    @Test
    public void test01() {
        assertEquals("Command\tComment\tLocale\tService\tApp\tUtterance\tLabel\tArg1\nreplaceSel\t\t\t\t\t\t\t", getTsv(""));
    }

    @Test
    public void test02() {
        assertEquals("Command\tComment\tLocale\tService\tApp\tUtterance\tLabel\tArg1\nreplaceSel\t\t\t\t\t\t$\t\\$", getTsv("$"));
    }

    @Test
    public void test03() {
        assertEquals("Command\tComment\tLocale\tService\tApp\tUtterance\tLabel\tArg1\nreplaceSel\t\t\t\t\t\t\\\t\\\\", getTsv("\\"));
    }

    @Test
    public void test04() {
        assertEquals("Command\tComment\tLocale\tService\tApp\tUtterance\tLabel\tArg1\nreplaceSel\t\t\t\t\t\t@sel\t@sel", getTsv("@sel"));
    }

    @Test
    public void test05() {
        assertEquals("Command\tComment\tLocale\tService\tApp\tUtterance\tLabel\tArg1\nreplaceSel\t\t\t\t\t\t \t ", getTsv(" "));
    }

    @Test
    public void testMakeCommand01() {
        assertEquals("\t\t\t\t\t\t\t\t\t", getCommandTsv(""));
    }

    @Test
    public void testMakeCommand02() {
        assertEquals("\t\t\t\t\t\ttest\ttest\t\t", getCommandTsv("test"));
    }

    @Test
    public void testMakeCommand03() {
        assertEquals("\t\t\t\t\t\t\\$\t$\t\t", getCommandTsv("$"));
    }

    @Test
    public void testMakeCommand04() {
        // Command Comment Locale Service App Utterance Replacement Label Arg1 Arg2
        assertEquals("REPL\t\t\t\t\t\t\tREPL ($) (\\)\t\\$\t\\\\", getCommand2Tsv("REPL"));
    }

    private String getTsv(String text) {
        return new RuleManager().addRecent(text, UTTERANCE, COMMENT, "").toTsv();
    }

    private String getCommandTsv(String text) {
        UtteranceRewriter.Rewrite rewrite = new UtteranceRewriter.Rewrite(text);
        Command command = new RuleManager().makeCommand(rewrite, UTTERANCE, COMMENT);
        return command.toTsv(UtteranceRewriter.DEFAULT_HEADER_COMMAND);
    }

    private String getCommand2Tsv(String id) {
        UtteranceRewriter.Rewrite rewrite = new UtteranceRewriter.Rewrite(id, "", new String[]{"$", "\\"}, new Command("", ""));
        Command command = new RuleManager().makeCommand(rewrite, UTTERANCE, COMMENT);
        return command.toTsv(UtteranceRewriter.DEFAULT_HEADER_COMMAND);
    }
}