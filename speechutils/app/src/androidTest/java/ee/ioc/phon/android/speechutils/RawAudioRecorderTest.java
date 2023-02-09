package ee.ioc.phon.android.speechutils;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class RawAudioRecorderTest {

    private RawAudioRecorder mRar;

    @Before
    public void before() {
        mRar = new RawAudioRecorder();
    }

    @Test
    public void test01() {
        assertTrue(mRar.getState().equals(AudioRecorder.State.READY));
    }

    @Test
    public void test02() {
        assertTrue(mRar.isPausing());
    }

}