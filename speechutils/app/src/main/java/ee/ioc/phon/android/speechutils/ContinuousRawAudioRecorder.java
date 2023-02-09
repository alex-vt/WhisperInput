package ee.ioc.phon.android.speechutils;

import androidx.annotation.RequiresPermission;

import java.util.concurrent.atomic.AtomicBoolean;

import ee.ioc.phon.android.speechutils.utils.AudioUtils;

import static android.Manifest.permission.RECORD_AUDIO;

/**
 * This class should be used for continuous recording of audio.
 * There are cases in which there's a need to constantly have the last X seconds
 * of audio buffer ready for processing. Such is the case of activation after hotword
 * detection. The hotword may be detected in an external device (e.g. DSP) which triggers
 * Android in some way and then audio buffer processing task should start.
 * There's a high probability that there's a mismatch between the trigger and the Android
 * recorder. Usually the trigger will be handled a few milliseconds (~100) AFTER the real
 * end-of-hotword and the Android recorder may have its own delay. So there's a need for
 * a mechanism that will allow the developer to compensate between the various delays and
 * enable audio processing of an audio buffer from a specific point in time (past, current
 * of future).
 * <p>
 * For the purpose of efficiency and reduction of garbage collection, the recorded buffer
 * is a cyclic one and the code handles the edge cases (gotten audio buffer is split
 * between the end of the recording buffer and the beginning).
 * <p>
 * The class also handles the cyclic buffer consumption. While consuming the recorded
 * buffer, the consumer is always behind or exactly on the producer pointer (chasing the
 * recording).
 * The cyclic structure can potentially create problematic situations wrt consumption.
 * For example: what happens if for some reason, the consumer doesn't consume fast enough
 * while the producer is advancing fast? At some point, the producer will pass the consumer
 * pointer and now, the gap between the left behind consumer and the fast running producer
 * is full with future data (data from consumer + X). This means that the buffer doesn't hold
 * anymore the data that connects the point in time of the consumer and the point in time
 * of the producer and there is no way that this can be solved. What can be done, and this
 * is handled by the code, is to have the ability to know about this problem.
 * We've introduced a concept of "sessions".
 * The consumer and the producer are on the same session if the time gap between them
 * (internally expressed in number of samples) is no more than X (size of the buffer in
 * time units). Once this time gap increases above X, the do not share the same session anymore
 * and the user can choose what to do with this state (start over, start from a number of millis
 * back etc...)
 */
public class ContinuousRawAudioRecorder extends AbstractAudioRecorder {

    private static final int DEFAULT_BUFFER_LENGTH_IN_MILLIS = 2000;
    private static final String LOG_FILTER = "continuous-recorder: ";

    private SessionStartPointer mSessionStartPointer = SessionStartPointer.beginningOfBuffer();
    private final AtomicBoolean mRecordingToFile = new AtomicBoolean(false);

    public static class SessionStartPointer {

        private int mSessionStartPointerMillis;
        private static SessionStartPointer mBeginningOfBufferPosition = new SessionStartPointer(Integer.MIN_VALUE);
        private static SessionStartPointer mNowPosition = new SessionStartPointer(0);

        private SessionStartPointer(int sessionStartPointerMillis) {
            setSessionStartPointerMillis(sessionStartPointerMillis);
        }

        private void setSessionStartPointerMillis(int sessionStartPointerMillis) {
            mSessionStartPointerMillis = sessionStartPointerMillis;
        }

        int getSessionStartPointerMillis() {
            // in case that no one set the buffer length, return start pointer as now
            if (this == mBeginningOfBufferPosition && mSessionStartPointerMillis == Integer.MIN_VALUE)
                return mNowPosition.getSessionStartPointerMillis();

            // in case that the requested start pointer is bigger than the buffer, return the buffer size
            if (this != mBeginningOfBufferPosition && mSessionStartPointerMillis < mBeginningOfBufferPosition.getSessionStartPointerMillis())
                return mBeginningOfBufferPosition.getSessionStartPointerMillis();

            return mSessionStartPointerMillis;
        }

        static void setRecordingBufferLengthMillis(int recordingBufferLengthMillis) {
            mBeginningOfBufferPosition.setSessionStartPointerMillis(-Math.abs(recordingBufferLengthMillis));
        }

        public static SessionStartPointer beginningOfBuffer() {
            return mBeginningOfBufferPosition;
        }

        public static SessionStartPointer now() {
            return mNowPosition;
        }

        public static SessionStartPointer someMillisBack(int millisBackToStartTheSessionFrom) {
            return new SessionStartPointer(-Math.abs(millisBackToStartTheSessionFrom));
        }

        public static SessionStartPointer someSecondsBack(int secondsBackToStartTheSessionFrom) {
            return new SessionStartPointer(-Math.abs(secondsBackToStartTheSessionFrom * 1000));
        }

        public static SessionStartPointer someMillisForward(int millisForwardToStartTheSessionFrom) {
            return new SessionStartPointer(Math.abs(millisForwardToStartTheSessionFrom));
        }

        public static SessionStartPointer someSecondsForward(int secondsForwardToStartTheSessionFrom) {
            return new SessionStartPointer(Math.abs(secondsForwardToStartTheSessionFrom * 1000));
        }

        public static SessionStartPointer someMillisFromLatest(int millisFromLatestToStartTheSessionFrom_NegativeIsPast_PositiveIsFuture) {
            return new SessionStartPointer(millisFromLatestToStartTheSessionFrom_NegativeIsPast_PositiveIsFuture);
        }

        public static SessionStartPointer someSecondsFromLatest(int secondsFromLatestToStartTheSessionFrom_NegativeIsPast_PositiveIsFuture) {
            return new SessionStartPointer(secondsFromLatestToStartTheSessionFrom_NegativeIsPast_PositiveIsFuture * 1000);
        }
    }

    @RequiresPermission(RECORD_AUDIO)
    public ContinuousRawAudioRecorder(int audioSource, int sampleRate, int recordingBufferLengthMillis) {
        super(audioSource, sampleRate, recordingBufferLengthMillis, true);

        // this is very important. We introduce the buffer length to the SessionStartPointer object
        SessionStartPointer.setRecordingBufferLengthMillis(recordingBufferLengthMillis);

        try {
            int bufferSize = getBufferSize();
            int framePeriod = bufferSize / (2 * RESOLUTION_IN_BYTES * CHANNELS);
            createRecorder(audioSource, sampleRate, bufferSize);
            createBuffer(framePeriod);
            setState(State.READY);
        } catch (Exception e) {
            if (e.getMessage() == null) {
                handleError("Unknown error occurred while initializing recorder");
            } else {
                handleError(e.getMessage());
            }
        }
    }

    @RequiresPermission(RECORD_AUDIO)
    public ContinuousRawAudioRecorder(int sampleRate, int recordingBufferLengthMillis) {
        this(DEFAULT_AUDIO_SOURCE, sampleRate, recordingBufferLengthMillis);
    }

    @RequiresPermission(RECORD_AUDIO)
    public ContinuousRawAudioRecorder(int sampleRate) {
        this(DEFAULT_AUDIO_SOURCE, sampleRate, DEFAULT_BUFFER_LENGTH_IN_MILLIS);
    }

    @RequiresPermission(RECORD_AUDIO)
    public ContinuousRawAudioRecorder() {
        this(DEFAULT_AUDIO_SOURCE, DEFAULT_SAMPLE_RATE, DEFAULT_BUFFER_LENGTH_IN_MILLIS);
    }

    public String getContentType() {
        return "audio/x-raw, layout=(string)interleaved, rate=(int)" + getSampleRate() + ", format=(string)S16LE, channels=(int)1";
    }

    public ContinuousRawAudioRecorder setSessionStartPointer(SessionStartPointer sessionStartPointer) {
        mSessionStartPointer = sessionStartPointer;
        return this;
    }

    private int calculateNumOfSamplesToGoBack(int startPos) {
        // if the consumed session is not the same as the recorded session
        // get the data from the beginning of the buffer/desired session start pointer
        if (!isRecordedSessionSameAsConsumedSession()) {
            Log.i(LOG_FILTER + "Recorded session and consumed session are NOT the same. Grabbing the data from the session start position");

            // there are cases in which due to delay in the recorder wrt the real world, we will need
            // to wait for the exact moment. The session start point should be based on trial and error
            if (mSessionStartPointer.getSessionStartPointerMillis() > 0) {
                try {
                    Thread.sleep(mSessionStartPointer.getSessionStartPointerMillis());
                } catch (InterruptedException e) {
                }

                return getNumOfSamplesIn(SessionStartPointer.now().getSessionStartPointerMillis());
            }

            int numOfSamplesToGoBack = getNumOfSamplesIn(mSessionStartPointer.getSessionStartPointerMillis());
            if (numOfSamplesToGoBack > mRecording.length)
                return getNumOfSamplesIn(SessionStartPointer.beginningOfBuffer().getSessionStartPointerMillis());

            return numOfSamplesToGoBack;
        }

        Log.i(LOG_FILTER + "Recorded session and consumed session are the same. Working according to the consumed pointer");
        if (startPos == getLength()) {
            Log.i(LOG_FILTER + "Consumed session pointer still points to the recorded session pointer. Nothing to do or to return");
            return -1;
        }

        int numOfSamplesToGoBack = getLength() - startPos;
        if (numOfSamplesToGoBack < 0) // cyclic buffer case
            numOfSamplesToGoBack += mRecording.length;

        Log.i(LOG_FILTER + "Consumed session pointer is behind the recorded session pointer by: " + numOfSamplesToGoBack + " samples");
        return numOfSamplesToGoBack;
    }

    @Override
    protected byte[] getCurrentRecording(int startPos) {

        int numOfSamplesToGoBack = calculateNumOfSamplesToGoBack(startPos);
        if (numOfSamplesToGoBack <= 0) {
            Log.i(LOG_FILTER + "There are no samples that we need to take from the recording");
            return null;
        }

        return getCurrentRecordingFrom(numOfSamplesToGoBack);
    }

    private byte[] getCurrentRecordingFrom(int numOfSamplesToGoBack) {
        byte[] buffer = new byte[numOfSamplesToGoBack];
        int currentLength = getLength();
        int potentialStartSample = currentLength - numOfSamplesToGoBack;

        if (potentialStartSample >= 0) {
            Log.i(LOG_FILTER + "Start sample in the recording is a positive one. Copying from position: " + potentialStartSample + ", " + numOfSamplesToGoBack + " bytes");
            System.arraycopy(mRecording, potentialStartSample, buffer, 0, numOfSamplesToGoBack);
        } else {
            if (!mRecordingBufferIsFullWithData) {
                Log.i(LOG_FILTER + "Start sample in the recording is a negative one. The buffer did not pass one cycle yet. Copying from position: 0, " + currentLength + " bytes");
                System.arraycopy(mRecording, 0, buffer, 0, currentLength);
            } else {
                // the potential start sample is out of the boundaries of the array to the negative side
                potentialStartSample = Math.abs(potentialStartSample);
                Log.i(LOG_FILTER + "Start sample in the recording is a negative one. The buffer passed at least one cycle. Copying from position: " + (mRecording.length - potentialStartSample) + ", " + potentialStartSample + " bytes and from position: 0, " + currentLength + " bytes");
                System.arraycopy(mRecording, mRecording.length - potentialStartSample, buffer, 0, potentialStartSample);
                System.arraycopy(mRecording, 0, buffer, potentialStartSample, currentLength);
            }
        }

        return buffer;
    }

    public byte[] pcmToWav(byte[] pcm) {
        return AudioUtils.getRecordingAsWav(pcm, getSampleRate(), RESOLUTION_IN_BYTES, CHANNELS);
    }

    private byte[] createWavHeader(int pcmDataLength) {
        return AudioUtils.getWavHeader(pcmDataLength, getSampleRate(), RESOLUTION_IN_BYTES, CHANNELS);
    }

    public void dumpBufferToWavFile(String wavFileFullPath) {
        SessionStartPointer sessionStartPointer = mSessionStartPointer;
        setSessionStartPointer(SessionStartPointer.beginningOfBuffer());
        AudioUtils.saveWavToFile(wavFileFullPath, pcmToWav(consumeRecording()), false);
        setSessionStartPointer(sessionStartPointer);
    }

    public void startRecording(final String wavFileFullPath) {
        if (!mRecordingToFile.compareAndSet(false, true))
            return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                // in case that someone stopped the recording before the thread could start
                if (!mRecordingToFile.get())
                    return;

                int pcmDataLength = 0;
                byte[] pcmData;
                AtomicBoolean firstBuffer = new AtomicBoolean(true);

                while (mRecordingToFile.get()) {

                    // No need to endlessly poll the recording. It will work without the sleep well but
                    // every 50ms is also good (keeps the CPU happier than without the sleep)
                    try {
                        Thread.sleep(50L);
                    } catch (InterruptedException e) {
                        mRecordingToFile.set(false);
                        return;
                    }

                    pcmData = consumeRecording();
                    if (pcmData == null || pcmData.length == 0)
                        continue;

                    pcmDataLength += pcmData.length;
                    if (firstBuffer.compareAndSet(true, false))
                        AudioUtils.saveWavToFile(wavFileFullPath, pcmToWav(pcmData), false);
                    else
                        AudioUtils.saveWavToFile(wavFileFullPath, pcmData, true);
                }

                //now rewrite the wav header according to the new size
                AudioUtils.saveWavHeaderToFile(wavFileFullPath, createWavHeader(pcmDataLength));
            }
        }).start();
    }

    public void stopRecording() {
        mRecordingToFile.compareAndSet(true, false);
    }
}
