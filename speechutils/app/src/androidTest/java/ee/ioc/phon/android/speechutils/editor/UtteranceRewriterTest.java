package ee.ioc.phon.android.speechutils.editor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.util.Pair;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
public class UtteranceRewriterTest {

    private static final List<Command> COMMANDS;

    private static final SortedMap<Integer, String> HEADER;

    static {
        SortedMap<Integer, String> aMap = new TreeMap<>();
        aMap.put(0, UtteranceRewriter.HEADER_UTTERANCE);
        aMap.put(1, UtteranceRewriter.HEADER_REPLACEMENT);
        aMap.put(2, UtteranceRewriter.HEADER_COMMAND);
        aMap.put(3, UtteranceRewriter.HEADER_ARG1);
        aMap.put(4, UtteranceRewriter.HEADER_ARG2);
        HEADER = Collections.unmodifiableSortedMap(aMap);
    }

    static {
        List<Command> list = new ArrayList<>();
        list.add(new Command("s/(.*)/(.*)/", "X", "replace", new String[]{"$1", "$2"}));
        // Map <1><2><3> into 1, 2, 3
        // TODO: is there a better way
        list.add(new Command("<([^>]+)>", "$1, "));
        list.add(new Command(", K6_STOP, ", "<K6_STOP>"));
        COMMANDS = Collections.unmodifiableList(list);
    }

    private UtteranceRewriter mUr;

    @Before
    public void before() {
        mUr = new UtteranceRewriter(COMMANDS, HEADER);
    }

    @Test
    public void test01() {
        Command command = new Command("s/(.*)/(.*)/", "X", "replace", new String[]{"$1", "$2"});
        assertThat(command.toTsv(HEADER), is("s/(.*)/(.*)/\tX\treplace\t$1\t$2"));
        Pair<String, String[]> pair = command.parse("s/_/a/");
        assertThat(pair.first, is("X"));
        assertThat(pair.second[0], is("_"));
        assertThat(pair.second[1], is("a"));
    }

    @Test
    public void test02() {
        rewrite("s/_/a/", "replace (_) (a)", "X");
    }

    @Test
    public void test03() {
        rewrite("<1><2><3><K6_STOP>", null, "1, 2, 3<K6_STOP>");
    }

    @Test
    public void test04() {
        UtteranceRewriter ur = new UtteranceRewriter("");
        assertThat(ur.toTsv(), is("Utterance"));
    }

    @Test
    public void test05() {
        UtteranceRewriter ur = new UtteranceRewriter("utt");
        assertThat(ur.toTsv(), is("Utterance\nutt"));
    }

    @Test
    public void test06() {
        UtteranceRewriter ur = new UtteranceRewriter("utt1\nutt2");
        assertThat(ur.toTsv(), is("Utterance\nutt1\nutt2"));
    }

    @Test
    public void test07() {
        UtteranceRewriter ur = new UtteranceRewriter("Utterance\nutt1\nutt2");
        assertThat(ur.toTsv(), is("Utterance\nutt1\nutt2"));
    }

    @Test
    public void test08() {
        UtteranceRewriter ur = new UtteranceRewriter("utt1\trepl1\tignored1\nutt2\trepl2\tignored2\n");
        assertThat(ur.toTsv(), is("Utterance\tReplacement\nutt1\trepl1\nutt2\trepl2"));
    }

    @Test
    public void test09() {
        UtteranceRewriter ur = new UtteranceRewriter("Utterance\tReplacement\nutt1\trepl1\tignored1\nutt2\trepl2\tignored2\n");
        assertThat(ur.toTsv(), is("Utterance\tReplacement\nutt1\trepl1\nutt2\trepl2"));
    }

    @Test
    public void test10() {
        UtteranceRewriter ur = new UtteranceRewriter("Ignored\tUtterance\n\n#\t#\nignored\tutt1\n\t\t\nignored2\tutt2");
        assertThat(ur.toTsv(), is("Utterance\nutt1\nutt2"));
    }

    @Test
    public void test11() {
        UtteranceRewriter ur = new UtteranceRewriter("" +
                "Utterance\tReplacement\tCommand\tArg1\tIgnored\n" +
                "utt\trepl\t\t\tignored\n");
        assertThat(ur.toTsv(), is("Utterance\tReplacement\tCommand\tArg1\nutt\trepl\t\t"));
        assertThat(ur.getRewrite("p utt s").mStr, is("p repl s"));
    }

    @Test
    public void test12() {
        String tsv = "Utterance\tReplacement\r\nf1\tf2\rg1\tg2\n\n";
        UtteranceRewriter ur = new UtteranceRewriter(tsv);
        assertThat(ur.toTsv(), is("Utterance\tReplacement\nf1\tf2\ng1\tg2"));
    }

    private void rewrite(String str1, String str2, String str3) {
        UtteranceRewriter.Rewrite rewrite = mUr.getRewrite(str1);
        assertThat(rewrite.ppCommand(), is(str2));
        assertThat(rewrite.mStr, is(str3));
    }

}