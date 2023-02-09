package ee.ioc.phon.android.speechutils.editor;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class CommandTest {

    // Argument arrays are trimmed, i.e. nulls and empties are removed from the end.
    // However, whitespace is not removed from the values.
    private final Command c = makeCommand("c", "replaceSel", new String[]{"c"});
    private final Command c1 = makeCommand("c1", "replaceSel", new String[]{"c"});
    private final Command c2 = makeCommand("c2", "replaceSel", new String[]{"c2"});
    private final Command c3 = makeCommand("c3", "replaceSel", new String[]{"c", "", null});
    private final Command c4 = makeCommand("c4", "replaceSel", new String[]{"c", "d"});
    private final Command c5 = makeCommand("c5", "replaceSel", new String[]{});
    private final Command c6 = makeCommand("c6", "replaceSel", new String[]{"c", null, null});
    private final Command c7 = makeCommand("c7", "replaceSel", new String[]{"c", " "});

    private final Command empty1 = makeCommand("empty1", "id", new String[]{});
    private final Command empty2 = makeCommand("empty2", "id", new String[]{""});
    private final Command empty3 = makeCommand("empty3", "id", new String[]{null});
    private final Command empty4 = makeCommand("empty4", "id", null);

    @Test
    public void test01() {
        assertTrue(c.equalsCommand(c1));
    }

    @Test
    public void test02() {
        assertFalse(c.equalsCommand(c2));
    }

    @Test
    public void test03() {
        assertTrue(c.equalsCommand(c3));
    }

    @Test
    public void test04() {
        assertFalse(c.equalsCommand(c4));
    }

    @Test
    public void test05() {
        assertFalse(c.equalsCommand(c5));
    }

    @Test
    public void test06() {
        assertTrue(c.equalsCommand(c6));
    }

    @Test
    public void test07() {
        assertFalse(c.equalsCommand(c7));
    }

    @Test
    public void test08() {
        assertTrue(empty1.equalsCommand(empty2) && empty2.equalsCommand(empty3) && empty3.equalsCommand(empty4) && empty4.equalsCommand(empty1));
    }

    @Test
    public void testMakeUtt01() {
        assertEquals(makeCommand("matcher").makeUtt(), "matcher");
    }

    @Test
    public void testMakeUtt02() {
        assertEquals(makeCommand("mat*cher*").makeUtt(), "mache");
    }

    @Test
    public void testMakeUtt03() {
        assertEquals(makeCommand("([123]00)").makeUtt(), "100");
    }

    @Test
    public void testMakeUtt04() {
        assertEquals(makeCommand("((?:3|2|1)00)").makeUtt(), "300");
    }

    @Test
    public void testMakeUtt05() {
        assertEquals(makeCommand("a b? c").makeUtt(), "a  c");
    }

    @Test
    public void testMakeUtt06() {
        assertEquals(makeCommand("ab?c|d").makeUtt(), "ac");
    }

    @Test
    public void testMakeUtt07() {
        assertEquals(makeCommand("a b? c|d").makeUtt(), "a  c");
    }

    private Command makeCommand(String label, String id, String[] args) {
        return new Command(label, "", null, null, null, Pattern.compile("^" + label + "$"), "", id, args);
    }

    private Command makeCommand(String utt) {
        return new Command("^" + utt + "$", "");
    }
}