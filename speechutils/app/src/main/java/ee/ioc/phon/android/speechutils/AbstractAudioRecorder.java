/*
 * Copyright 2011-2016, Institute of Cybernetics at Tallinn University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ee.ioc.phon.android.speechutils;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Build;

import androidx.annotation.RequiresPermission;

import java.util.concurrent.atomic.AtomicLong;

import ee.ioc.phon.android.speechutils.utils.AudioUtils;

import static android.Manifest.permission.RECORD_AUDIO;

public abstract class AbstractAudioRecorder implements AudioRecorder {

    private static final int RESOLUTION = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_MULTIPLIER = 4; // was: 2
    private static final int DEFAULT_BUFFER_LENGTH_IN_MILLIS = 35000;

    private AudioRecord mRecorder = null;

    private double mAvgEnergy = 0;

    private final int mSampleRate;
    private final int mSamplesInOneSec;
    private final int mSamplesInOneMilliSec;
    private final boolean mAlwaysListen;

    // Recorder state
    private State mState;

    // The complete space into which the recording in written.
    // Its maximum length is about:
    // 2 (bytes) * 1 (channels) * 30 (max rec time in seconds) * 44100 (times per second) = 2 646 000 bytes
    // but typically is:
    // 2 (bytes) * 1 (channels) * 20 (max rec time in seconds) * 16000 (times per second) = 640 000 bytes
    final byte[] mRecording;

    // TODO: use: mRecording.length instead
    private int mRecordedLength = 0;
    private AtomicLong mRecordedSessionId = new AtomicLong(0L);
    boolean mRecordingBufferIsFullWithData = false;
    private final int mRecordingBufferLengthMillis;

    // The number of bytes the client has already consumed
    private int mConsumedLength = 0;
    private AtomicLong mConsumedSessionId = new AtomicLong(0L);

    // Buffer for output
    private byte[] mBuffer;

    protected AbstractAudioRecorder(int audioSource, int sampleRate, int recordingBufferLengthMillis, boolean alwaysListen) {
        mSampleRate = sampleRate;
        // E.g. 1 second of 16kHz 16-bit mono audio takes 32000 bytes.
        mSamplesInOneSec = RESOLUTION_IN_BYTES * CHANNELS * mSampleRate;
        mSamplesInOneMilliSec = (int) ((double) mSamplesInOneSec / 1000.0);
        mRecordingBufferLengthMillis = recordingBufferLengthMillis;
        mRecording = new byte[mSamplesInOneMilliSec * mRecordingBufferLengthMillis];
        mAlwaysListen = alwaysListen;
    }

    protected AbstractAudioRecorder(int audioSource, int sampleRate) {
        this(audioSource, sampleRate, DEFAULT_BUFFER_LENGTH_IN_MILLIS, false);
    }

    @RequiresPermission(RECORD_AUDIO)
    protected AudioRecord createRecorder(int audioSource, int sampleRate, int bufferSize) {
        if (mRecorder != null)
            release();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mRecorder = SpeechAudioRecord.create(audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, RESOLUTION, bufferSize, false, false, false);
        } else {
            mRecorder = new SpeechRecord(audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, RESOLUTION, bufferSize, false, false, false);
        }
        if (getSpeechRecordState() != SpeechRecord.STATE_INITIALIZED) {
            throw new IllegalStateException("SpeechRecord initialization failed");
        }

        return mRecorder;
    }

    // TODO: remove
    protected void createBuffer(int framePeriod) {
        mBuffer = new byte[framePeriod * RESOLUTION_IN_BYTES * CHANNELS];
    }

    protected int getBufferSize() {
        int minBufferSizeInBytes = SpeechRecord.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_IN_MONO, RESOLUTION);
        if (minBufferSizeInBytes == SpeechRecord.ERROR_BAD_VALUE) {
            throw new IllegalArgumentException("SpeechRecord.getMinBufferSize: parameters not supported by hardware");
        } else if (minBufferSizeInBytes == SpeechRecord.ERROR) {
            Log.e("SpeechRecord.getMinBufferSize: unable to query hardware for output properties");
            minBufferSizeInBytes = mSampleRate * (120 / 1000) * RESOLUTION_IN_BYTES * CHANNELS;
        }
        int bufferSize = BUFFER_SIZE_MULTIPLIER * minBufferSizeInBytes;
        Log.i("SpeechRecord buffer size: " + bufferSize + ", min size = " + minBufferSizeInBytes);
        return bufferSize;
    }

    /**
     * Returns the recorded bytes since the last call, and resets the recording.
     *
     * @return bytes that have been recorded since this method was last called
     */
    public synchronized byte[] consumeRecordingAndTruncate() {
        int len = getConsumedLength();
        byte[] bytes = getCurrentRecording(len);
        setRecordedLength(0);
        setConsumedLength(0);
        return bytes;
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    protected int getNumOfSamplesIn(int millis) {
        return Math.abs(millis) * mSamplesInOneMilliSec;
    }

    protected boolean isRecordedSessionSameAsConsumedSession() {
        return mRecordedSessionId.get() == mConsumedSessionId.get();
    }

    /**
     * Checking of the read status.
     * The total recording array has been pre-allocated (e.g. for 35 seconds of audio).
     * If it gets full (status == -5) then the recording is stopped.
     */
    protected int getStatus(int numOfBytes, int len) {
        Log.i("Read bytes: request/actual: " + len + "/" + numOfBytes);
        if (numOfBytes < 0) {
            Log.e("AudioRecord error: " + numOfBytes);
            return numOfBytes;
        }
        if (numOfBytes > len) {
            Log.e("Read more bytes than is buffer length: " + numOfBytes + ": " + len);
            return -100;
        } else if (numOfBytes == 0) {
            Log.e("Read zero bytes");
            return -200;
        } else if (mRecording.length < mRecordedLength + numOfBytes) {
            Log.e("Recorder buffer overflow: " + mRecordedLength);
            return -300;
        }
        return 0;
    }

    /**
     * Check if the consume pointer was crossed by the recorded pointer. As long as the consume
     * pointer was not crossed, the consumption of the buffer may continue as usual and no sound gap
     * will occur. Once the consume pointer was crossed (e.g. it was on sample 1000 and prior to this
     * read the recorder was on sample 750 and now that it read the new sample it's on 1500), there's
     * an audio gap between the consumer and the recorder that can not be filled (data is lost with
     * no ability to get it back). Whenever this kind of cross occurs, the calling code changed the
     * session id of the recorder so that if consume is called (from ContinuousRawAudioRecorder),
     * it will not assume that the data is complete and could be fetched but it will act according to
     * the SessionStartPointer configured (e.g. read the buffer from the beginning, from now, or from
     * now - X millis)
     *
     * @param reachedTheEndOfRecordingBuffer - in case that in the read before the call to this method the recorder
     *                                       passed the end of the buffer and returned to the beginning
     * @param numOfBytesRead                 - in the reading process
     * @return true/false according to the above logic
     */
    private boolean isConsumePointerCrossed(boolean reachedTheEndOfRecordingBuffer, int numOfBytesRead) {
        return numOfBytesRead > 0 &&
                mRecordingBufferIsFullWithData &&
                isRecordedSessionSameAsConsumedSession() &&
                ((mRecordedLength - numOfBytesRead < mConsumedLength && mRecordedLength >= mConsumedLength) ||
                        (reachedTheEndOfRecordingBuffer && mConsumedLength < mRecordedLength));
    }

    public long markNewRecordingSession() {
        return mRecordedSessionId.incrementAndGet();
    }

    /**
     * Copy data from the given recorder into the given buffer, and append to the complete recording.
     * public int read (byte[] audioData, int offsetInBytes, int sizeInBytes)
     */
    @RequiresPermission(RECORD_AUDIO)
    protected int read(AudioRecord recorder, byte[] buffer) {
        int len = buffer.length;
        int numOfBytes = recorder.read(buffer, 0, len);
        // handling mediaserver crashes here
        // it doesn't happen a lot but it happens and the way to handle it is to fully restart
        // the audio recorder
        if (numOfBytes == 0 && mAlwaysListen) {
            consumeRecordingAndTruncate();
            mBuffer = new byte[mBuffer.length];
            createRecorder(recorder.getAudioSource(), recorder.getSampleRate(), getBufferSize());
            start();
        }

        int status = getStatus(numOfBytes, len);
        boolean reachedTheEndOfRecordingBuffer = false;
        // if we need to keep on listening, when reaching the end of the recorded buffer,
        // continue to write from the beginning. thus, we have a cyclic buffer
        if (mAlwaysListen && status == -300) {
            reachedTheEndOfRecordingBuffer = true;
            status = 0;
            // for use when consuming the recorded buffer, the buffer is now in it's cyclic phase
            if (!mRecordingBufferIsFullWithData)
                mRecordingBufferIsFullWithData = true;
        }

        if (status == 0 && numOfBytes >= 0) {
            if (!reachedTheEndOfRecordingBuffer) {
                // arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
                // numOfBytes <= len, typically == len, but at the end of the recording can be < len.
                System.arraycopy(buffer, 0, mRecording, mRecordedLength, numOfBytes);
                mRecordedLength += numOfBytes;
            } else {
                int numOfBytesBeforeCyclic = mRecording.length - mRecordedLength;
                System.arraycopy(buffer, 0, mRecording, mRecordedLength, numOfBytesBeforeCyclic);
                System.arraycopy(buffer, numOfBytesBeforeCyclic, mRecording, 0, numOfBytes - numOfBytesBeforeCyclic);

                mRecordedLength = numOfBytes - numOfBytesBeforeCyclic;
            }

            // increment the recorded session id in case that the consume pointer was crossed
            if (isConsumePointerCrossed(reachedTheEndOfRecordingBuffer, numOfBytes)) {
                Log.i("recorder session changed. mRecordedLength was: " + (mRecordedLength - numOfBytes) + " and now it is: " + mRecordedLength + " while the mConsumedLength is: " + mConsumedLength);
                markNewRecordingSession();
            }
        }

        return mAlwaysListen ? 0 : status;
    }


    /**
     * @return recorder state
     */
    public State getState() {
        return mState;
    }

    protected void setState(State state) {
        mState = state;
    }


    /**
     * @return bytes that have been recorded since the beginning
     */
    public byte[] getCompleteRecording() {
        return getCurrentRecording(0);
    }


    /**
     * @return bytes that have been recorded since the beginning, with wav-header
     */
    public byte[] getCompleteRecordingAsWav() {
        return getRecordingAsWav(getCompleteRecording(), mSampleRate);
    }


    public static byte[] getRecordingAsWav(byte[] pcm, int sampleRate) {
        return AudioUtils.getRecordingAsWav(pcm, sampleRate, RESOLUTION_IN_BYTES, CHANNELS);
    }

    /**
     * @return bytes that have been recorded since this method was last called
     */
    public synchronized byte[] consumeRecording() {
        byte[] bytes = getCurrentRecording(mConsumedLength);
        if (bytes == null)
            return null;

        // this is to avoid race (set the consumed length to be the recorded length though
        // the last recording was empty while recorded length moved on - thus we always miss
        // a part of the recording)
        mConsumedLength = mRecordedLength;
        mConsumedSessionId.set(mRecordedSessionId.get());
        return bytes;
    }

    protected byte[] getCurrentRecording(int startPos) {
        int len = getLength() - startPos;
        byte[] bytes = new byte[len];
        System.arraycopy(mRecording, startPos, bytes, 0, len);
        Log.i("Copied (raw) from pos: " + startPos + ", bytes: " + bytes.length);
        return bytes;
    }

    protected int getConsumedLength() {
        return mConsumedLength;
    }

    protected void setConsumedLength(int len) {
        mConsumedLength = len;
    }

    protected void setRecordedLength(int len) {
        mRecordedLength = len;
    }

    public int getLength() {
        return mRecordedLength;
    }

    /**
     * @return <code>true</code> iff a speech-ending pause has occurred at the end of the recorded data
     */
    public boolean isPausing() {
        double pauseScore = getPauseScore();
        Log.i("Pause score: " + pauseScore);
        return pauseScore > 7;
    }

    /**
     * @return volume indicator that shows the average volume of the last read buffer
     */
    public float getRmsdb() {
        long sumOfSquares = getRms(mRecordedLength, mBuffer.length);
        double rootMeanSquare = Math.sqrt(sumOfSquares / (mBuffer.length / 2));
        if (rootMeanSquare > 1) {
            // TODO: why 10?
            return (float) (10 * Math.log10(rootMeanSquare));
        }
        return 0;
    }

    /**
     * <p>In order to calculate if the user has stopped speaking we take the
     * data from the last second of the recording, map it to a number
     * and compare this number to the numbers obtained previously. We
     * return a confidence score (0-INF) of a longer pause having occurred in the
     * speech input.</p>
     * <p/>
     * <p>TODO: base the implementation on some well-known technique.</p>
     *
     * @return positive value which the caller can use to determine if there is a pause
     */
    private double getPauseScore() {
        long t2 = getRms(mRecordedLength, mSamplesInOneSec);
        if (t2 == 0) {
            return 0;
        }
        double t = mAvgEnergy / t2;
        mAvgEnergy = (2 * mAvgEnergy + t2) / 3;
        return t;
    }

    /**
     * <p>Stops the recording (if needed) and releases the resources.
     * The object can no longer be used and the reference should be
     * set to null after a call to release().</p>
     */
    public synchronized void release() {
        if (mRecorder != null) {
            if (mRecorder.getRecordingState() == SpeechRecord.RECORDSTATE_RECORDING) {
                stop();
            }
            mRecorder.release();
            mRecorder = null;
        }
    }

    /**
     * <p>Starts the recording, and sets the state to RECORDING.</p>
     */
    public void start() {
        if (getSpeechRecordState() == SpeechRecord.STATE_INITIALIZED) {
            mRecorder.startRecording();
            if (mRecorder.getRecordingState() == SpeechRecord.RECORDSTATE_RECORDING) {
                setState(State.RECORDING);
                new Thread() {
                    @RequiresPermission(RECORD_AUDIO)
                    public void run() {
                        recorderLoop(mRecorder);
                    }
                }.start();
            } else {
                handleError("startRecording() failed");
            }
        } else {
            handleError("start() called on illegal state");
        }
    }


    /**
     * <p>Stops the recording, and sets the state to STOPPED.
     * If stopping fails then sets the state to ERROR.</p>
     */
    public void stop() {
        // We check the underlying SpeechRecord state trying to avoid IllegalStateException.
        // If it still occurs then we catch it.
        if (getSpeechRecordState() == SpeechRecord.STATE_INITIALIZED &&
                mRecorder.getRecordingState() == SpeechRecord.RECORDSTATE_RECORDING) {
            try {
                mRecorder.stop();
                setState(State.STOPPED);
            } catch (IllegalStateException e) {
                handleError("native stop() called in illegal state: " + e.getMessage());
            }
        } else {
            handleError("stop() called in illegal state");
        }
    }

    @RequiresPermission(RECORD_AUDIO)
    protected void recorderLoop(AudioRecord recorder) {
        while (recorder.getRecordingState() == SpeechRecord.RECORDSTATE_RECORDING) {
            int status = read(recorder, mBuffer);
            if (status < 0) {
                handleError("status = " + status);
                break;
            }
        }
    }


    private long getRms(int end, int span) {
        int begin = end - span;
        if (begin < 0) {
            begin = 0;
        }
        // make sure begin is even
        if (0 != (begin % 2)) {
            begin++;
        }

        long sum = 0;
        for (int i = begin; i < end; i += 2) {
            short curSample = getShort(mRecording[i], mRecording[i + 1]);
            sum += curSample * curSample;
        }
        return sum;
    }


    /*
     * Converts two bytes to a short (assuming little endian).
     * TODO: We don't need the whole short, just take the 2nd byte (the more significant one)
     * TODO: Most Android devices are little endian?
     */
    private static short getShort(byte argB1, byte argB2) {
        //if (ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN)) {
        //    return (short) ((argB1 << 8) | argB2);
        //}
        return (short) (argB1 | (argB2 << 8));
    }


    protected void handleError(String msg) {
        release();
        setState(State.ERROR);
        Log.e(msg);
    }

    private int getSpeechRecordState() {
        if (mRecorder == null) {
            return SpeechRecord.STATE_UNINITIALIZED;
        }
        return mRecorder.getState();
    }
}