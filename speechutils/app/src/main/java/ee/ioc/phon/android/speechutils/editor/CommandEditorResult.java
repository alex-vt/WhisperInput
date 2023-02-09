package ee.ioc.phon.android.speechutils.editor;

public class CommandEditorResult {

    private final boolean mSuccess;
    private final UtteranceRewriter.Rewrite mRewrite;

    public CommandEditorResult(boolean success, UtteranceRewriter.Rewrite rewrite) {
        mSuccess = success;
        mRewrite = rewrite;
    }

    public boolean isCommand() {
        return mRewrite.isCommand();
    }

    public UtteranceRewriter.Rewrite getRewrite() {
        return mRewrite;
    }

    public String getStr() {
        return mRewrite.getStr();
    }

    public boolean isSuccess() {
        return mSuccess;
    }
}