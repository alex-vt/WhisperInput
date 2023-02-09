package ee.ioc.phon.android.speechutils.editor;

public abstract class Op {
    public static final Op NO_OP = new Op("NO_OP") {

        @Override
        public Op run() {
            return null;
        }
    };

    private final String mId;
    private final int mCount;

    public Op(String id) {
        this(id, 1);
    }

    public Op(String id, int count) {
        mId = id;
        mCount = count;
    }

    public String toString() {
        if (mCount == 1) {
            return mId;
        }
        return mId + " " + mCount;
    }

    public boolean isNoOp() {
        return "NO_OP".equals(mId);
    }

    public abstract Op run();
}