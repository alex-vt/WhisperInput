package ee.ioc.phon.android.speechutils.editor;

import android.content.ComponentName;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleManager {

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private Pattern mLocalePattern;
    private Pattern mServicePattern;
    private Pattern mAppPattern;

    private CommandMatcher mCommandMatcher;

    public RuleManager() {
    }

    public Pattern getLocalePattern() {
        return mLocalePattern;
    }

    public Pattern getServicePattern() {
        return mServicePattern;
    }

    public Pattern getAppPattern() {
        return mAppPattern;
    }

    public void setMatchers(String locale, ComponentName service, ComponentName app) {
        if (locale == null) {
            mLocalePattern = null;
        } else {
            mLocalePattern = Pattern.compile(Pattern.quote(locale));
        }
        if (service == null) {
            mServicePattern = null;
        } else {
            mServicePattern = Pattern.compile(Pattern.quote(service.getClassName()));
        }
        if (app == null) {
            mAppPattern = null;
        } else {
            mAppPattern = Pattern.compile(Pattern.quote(app.getClassName()));
        }

        mCommandMatcher = CommandMatcherFactory.createCommandFilter(locale, service, app);
    }

    public CommandMatcher getCommandMatcher() {
        return mCommandMatcher;
    }

    /**
     * Adds the given editor result to the top of the given rewrite rules (as the most recent rule).
     */
    public UtteranceRewriter addRecent(CommandEditorResult editorResult, String rewrites) {
        UtteranceRewriter.Rewrite rewrite = editorResult.getRewrite();
        Calendar cal = Calendar.getInstance();
        String comment = DATE_FORMAT.format(cal.getTime());
        Command newCommand = makeCommand(rewrite, makeUtt(cal), comment);
        List<Command> commands = addToTop(newCommand, rewrites);
        return new UtteranceRewriter(commands, UtteranceRewriter.DEFAULT_HEADER_COMMAND);
    }

    /**
     * Adds the selection replacement command with the given text as an argument to the top of the rule list.
     * <p>
     * TODO: another option is to add it as a simple text replacement, which means that spacing and capitalization rules
     * would apply to it, and follow-up rules can rewrite it:
     * new Command(text, comment, mLocalePattern, mServicePattern, mAppPattern, makeUtt(cal), text, null);
     * TODO: we could also have a dedicated command (instead of replaceSel) that is optimized for inserting strings
     * without any support for @sel or regex replacements (and thus needs no escaping).
     */
    public UtteranceRewriter addRecent(String text, String rewrites) {
        Calendar cal = Calendar.getInstance();
        String comment = DATE_FORMAT.format(cal.getTime());
        return addRecent(text, makeUtt(cal), comment, rewrites);
    }

    public UtteranceRewriter addRecent(String text, Pattern utt, String comment, String rewrites) {
        String textEscaped = Matcher.quoteReplacement(text);
        Command newCommand = new Command(text, comment, mLocalePattern, mServicePattern, mAppPattern, utt, "", CommandEditorManager.REPLACE_SEL, new String[]{textEscaped});
        List<Command> commands = addToTop(newCommand, rewrites);
        return new UtteranceRewriter(commands, UtteranceRewriter.DEFAULT_HEADER_REPLACE_SEL);
    }

    /**
     * Adds the given editor result to the given rewrite rules. And sorts them by frequency.
     * The frequency info is stored in the comment-field.
     */
    public UtteranceRewriter addFrequent(CommandEditorResult editorResult, String rewritesAsStr) {
        UtteranceRewriter.Rewrite rewrite = editorResult.getRewrite();
        List<Command> commands = addRuleFreq(rewrite, rewritesAsStr);
        return new UtteranceRewriter(commands, UtteranceRewriter.DEFAULT_HEADER_COMMAND);
    }

    /**
     * Adds the given rewrite to the rule list and sorts this by frequency.
     */
    private List<Command> addRuleFreq(UtteranceRewriter.Rewrite rewrite, @NonNull String rewrites) {
        Command newCommand = makeCommand(rewrite, makeUtt(Calendar.getInstance()), "1");
        List<Command> oldList = new UtteranceRewriter(rewrites).getCommands();
        List<Command> newList = new ArrayList<>();
        boolean isNewCommand = true;
        for (Command c : oldList) {
            // TODO: do not require command to exist yet, compare directly the relevant attributes
            if (isNewCommand && newCommand.equalsCommand(c) && matches(c)) {
                int count = getCount(c) + 1;
                newList.add(makeCommand(rewrite, c.getUtterance(), "" + count));
                isNewCommand = false;
            } else {
                newList.add(c);
            }
        }
        if (isNewCommand) {
            newList.add(newCommand);
        }

        Collections.sort(newList, (c1, c2) -> {
            return Integer.compare(getCount(c2), getCount(c1));
        });

        return newList;
    }

    private List<Command> addToTop(Command newCommand, String rewrites) {
        List<Command> oldList = new UtteranceRewriter(rewrites).getCommands();
        List<Command> newList = new ArrayList<>();

        Command oldCommand = null;
        for (Command c : oldList) {
            if (oldCommand == null && newCommand.equalsCommand(c) && matches(c)) {
                oldCommand = c;
            } else {
                newList.add(c);
            }
        }
        if (oldCommand == null) {
            newList.add(0, newCommand);
        } else {
            // TODO: update the timestamp (comment field)
            newList.add(0, oldCommand);
        }
        return newList;
    }

    private boolean matches(Command command) {
        if (mCommandMatcher == null) {
            return true;
        }
        return mCommandMatcher.matches(command.getLocale(), command.getService(), command.getApp());
    }

    /**
     * Converts a rewrite (i.e. a result of an application of a command) into a command. The new command
     * uses the given utterance pattern and comment. The other parts are reused from the rewrite.
     * The replacement is escaped: slashes ('\') and dollar signs ('$') will be given no special meaning.
     */
    public Command makeCommand(UtteranceRewriter.Rewrite rewrite, Pattern utt, String comment) {
        String repl = Matcher.quoteReplacement(rewrite.mStr);
        if (rewrite.isCommand()) {
            // We store the matched command, but change the utterance, comment, and the command matcher.
            // TODO: review this: use the (resolved) utterance as the label instead?
            String label = rewrite.getCommand().getLabel();
            if (label == null) {
                label = rewrite.ppCommand();
            }
            // Rewrite args is the output of command.parse, i.e. the evaluated args, but with escaping of "\" and "$".
            String[] args = new String[rewrite.mArgs.length];
            for (int i = 0; i < args.length; i++) {
                args[i] = Matcher.quoteReplacement(rewrite.mArgs[i]);
            }
            return new Command(label, comment, mLocalePattern, mServicePattern, mAppPattern, utt, repl, rewrite.mId, args);
        } else {
            return new Command(rewrite.mStr, comment, mLocalePattern, mServicePattern, mAppPattern, utt, repl, null);
        }
    }

    /**
     * Extracts an integer from the comment field, assuming that the "#f" table
     * stores the count in that field.
     * <p>
     * TODO: do not assume this
     *
     * @param command command
     * @return Frequency of the command
     */
    private static int getCount(Command command) {
        try {
            return Integer.parseInt(command.getComment());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Converts the given timestamp into an utterance.
     * TODO: maybe generate a pattern that would contain frequency, recency etc. info, and is easier to speak
     * TODO: maybe leave the utterance empty
     *
     * @param cal timestamp
     * @return Pattern that corresponds to the timestamp
     */
    public static Pattern makeUtt(Calendar cal) {
        long uttId = cal.getTimeInMillis();
        String uttAsStr = "^<" + uttId + ">$";
        return Pattern.compile(uttAsStr, Constants.REWRITE_PATTERN_FLAGS);
    }
}