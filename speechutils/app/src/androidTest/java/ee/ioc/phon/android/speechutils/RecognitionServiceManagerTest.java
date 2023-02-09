package ee.ioc.phon.android.speechutils;

import org.junit.Test;

import static ee.ioc.phon.android.speechutils.RecognitionServiceManager.makeLangLabel;
import static org.junit.Assert.assertEquals;

public class RecognitionServiceManagerTest {

    @Test
    public void makeLangLabel01() {
        assertEquals("?", makeLangLabel(null));
    }

    @Test
    public void makeLangLabel02() {
        assertEquals("Estonian (Estonia)", makeLangLabel("et-EE"));
    }

    @Test
    public void makeLangLabel03() {
        assertEquals("German (Austria)", makeLangLabel("de-AT"));
    }

    @Test
    public void makeLangLabel04() {
        assertEquals("Võro (Estonia)", makeLangLabel("vro-ee"));
    }

    @Test
    public void makeLangLabel05() {
        assertEquals("", makeLangLabel("und"));
    }
}