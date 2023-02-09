package ee.ioc.phon.android.speechutils.editor;

import android.content.ContentResolver;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * utterance = "go to position 1"
 * pattern = ("go to position (\d+)", "moveAbs", "$1")
 * command = moveAbs($1)
 * <p>
 * TODO: do not use Java split
 */
public class UtteranceRewriter {

    public static final String HEADER_LABEL = "Label";
    public static final String HEADER_COMMENT = "Comment";
    public static final String HEADER_LOCALE = "Locale";
    public static final String HEADER_SERVICE = "Service";
    public static final String HEADER_APP = "App";
    public static final String HEADER_UTTERANCE = "Utterance";
    public static final String HEADER_REPLACEMENT = "Replacement";
    public static final String HEADER_COMMAND = "Command";
    public static final String HEADER_ARG1 = "Arg1";
    public static final String HEADER_ARG2 = "Arg2";

    private static final Set<String> COLUMNS;

    // Support Windows and Mac line endings and consume empty lines.
    private static final String RE_LINE_SEPARATOR = "[\\r\\n]+";

    static {
        Set<String> aSet = new HashSet<>();
        aSet.add(HEADER_LABEL);
        aSet.add(HEADER_COMMENT);
        aSet.add(HEADER_LOCALE);
        aSet.add(HEADER_SERVICE);
        aSet.add(HEADER_APP);
        aSet.add(HEADER_UTTERANCE);
        aSet.add(HEADER_REPLACEMENT);
        aSet.add(HEADER_COMMAND);
        aSet.add(HEADER_ARG1);
        aSet.add(HEADER_ARG2);
        COLUMNS = Collections.unmodifiableSet(aSet);
    }

    // All fields
    public static final SortedMap<Integer, String> DEFAULT_HEADER;

    static {
        SortedMap<Integer, String> aMap0 = new TreeMap<>();
        aMap0.put(0, HEADER_LABEL);
        aMap0.put(1, HEADER_COMMENT);
        aMap0.put(2, HEADER_LOCALE);
        aMap0.put(3, HEADER_SERVICE);
        aMap0.put(4, HEADER_APP);
        aMap0.put(5, HEADER_UTTERANCE);
        aMap0.put(6, HEADER_REPLACEMENT);
        aMap0.put(7, HEADER_COMMAND);
        aMap0.put(8, HEADER_ARG1);
        aMap0.put(9, HEADER_ARG2);

        DEFAULT_HEADER = Collections.unmodifiableSortedMap(aMap0);
    }

    // All fields, but every row starts with Command.
    // This makes sure that rows do not start with "#" (which might be e.g. the 1st char of a Label,
    // but cannot be part of a Command). As a result the parser will not
    // drop any rows by treating them as comments.
    public static final SortedMap<Integer, String> DEFAULT_HEADER_COMMAND;

    static {
        SortedMap<Integer, String> aMapHeaderCommand = new TreeMap<>();
        aMapHeaderCommand.put(0, HEADER_COMMAND);
        aMapHeaderCommand.put(1, HEADER_COMMENT);
        aMapHeaderCommand.put(2, HEADER_LOCALE);
        aMapHeaderCommand.put(3, HEADER_SERVICE);
        aMapHeaderCommand.put(4, HEADER_APP);
        aMapHeaderCommand.put(5, HEADER_UTTERANCE);
        aMapHeaderCommand.put(6, HEADER_REPLACEMENT);
        aMapHeaderCommand.put(7, HEADER_LABEL);
        aMapHeaderCommand.put(8, HEADER_ARG1);
        aMapHeaderCommand.put(9, HEADER_ARG2);

        DEFAULT_HEADER_COMMAND = Collections.unmodifiableSortedMap(aMapHeaderCommand);
    }

    // Same as DEFAULT_HEADER_COMMAND but without REPLACEMENT and ARG2.
    // This is optimized for replaceSel commands used to store clipboard items.
    public static final SortedMap<Integer, String> DEFAULT_HEADER_REPLACE_SEL;

    static {
        SortedMap<Integer, String> aMapHeaderReplaceSel = new TreeMap<>();
        aMapHeaderReplaceSel.put(0, HEADER_COMMAND);
        aMapHeaderReplaceSel.put(1, HEADER_COMMENT);
        aMapHeaderReplaceSel.put(2, HEADER_LOCALE);
        aMapHeaderReplaceSel.put(3, HEADER_SERVICE);
        aMapHeaderReplaceSel.put(4, HEADER_APP);
        aMapHeaderReplaceSel.put(5, HEADER_UTTERANCE);
        aMapHeaderReplaceSel.put(6, HEADER_LABEL);
        aMapHeaderReplaceSel.put(7, HEADER_ARG1);

        DEFAULT_HEADER_REPLACE_SEL = Collections.unmodifiableSortedMap(aMapHeaderReplaceSel);
    }

    // Only utterance
    private static final SortedMap<Integer, String> DEFAULT_HEADER_1;

    static {
        SortedMap<Integer, String> aMap1 = new TreeMap<>();
        aMap1.put(0, HEADER_UTTERANCE);
        DEFAULT_HEADER_1 = Collections.unmodifiableSortedMap(aMap1);
    }

    // Utterance and replacement
    private static final SortedMap<Integer, String> DEFAULT_HEADER_2;

    static {
        SortedMap<Integer, String> aMap2 = new TreeMap<>();
        aMap2.put(0, HEADER_UTTERANCE);
        aMap2.put(1, HEADER_REPLACEMENT);
        DEFAULT_HEADER_2 = Collections.unmodifiableSortedMap(aMap2);
    }

    /**
     * A class that holds a text (mStr) and its possible interpretation as a command
     * with a name (mId) and a list of arguments (mArgs).
     */
    public static class Rewrite {
        public final String mId;
        public final String mStr;
        public final String[] mArgs;
        public final Command mCommand;

        public Rewrite(String str) {
            this(null, str, null, null);
        }

        public Rewrite(String id, String str, String[] args, Command command) {
            mId = id;
            mStr = str;
            mArgs = args;
            mCommand = command;
        }

        public boolean isCommand() {
            return mId != null;
        }

        public String getStr() {
            return mStr;
        }

        public Command getCommand() {
            return mCommand;
        }

        /**
         * Returns the pretty-printed command, e.g.
         * name (arg1) (arg2)
         * Empty trailing arguments are dropped.
         * TODO: do not drop the arguments (decide at parse time how many arguments a command has)
         *
         * @return pretty-printed command
         */
        public String ppCommand() {
            if (mArgs == null) {
                return mId;
            }
            int len = mArgs.length;
            int last = len - 1;
            // Search for the last non-empty argument position
            for (; last >= 0 && mArgs[last].isEmpty(); last--) ;
            String pp = mId;
            for (int i = 0; i <= last; i++) {
                pp += " (" + mArgs[i] + ")";
            }
            return pp;
        }
    }

    public static class CommandHolder {
        // Line that starts with "#" or consists entirely of 0 or more tabs.
        // Must be used with "lookingAt()".
        private static final Pattern PATTERN_EMPTY_ROW = Pattern.compile("#|\t*$");

        private final List<Command> mCommands;
        private final SortedMap<Integer, String> mHeader;
        private final SortedMap<Integer, String> mErrors = new TreeMap<>();

        public CommandHolder() {
            this(DEFAULT_HEADER_1, new ArrayList<>());
        }

        public CommandHolder(String inputHeader) {
            this(inputHeader, new ArrayList<>());
        }

        public CommandHolder(SortedMap<Integer, String> header, List<Command> commands) {
            mCommands = commands;
            mHeader = header;
        }

        public CommandHolder(String inputHeader, List<Command> commands) {
            boolean hasColumnUtterance = false;
            SortedMap<Integer, String> header = new TreeMap<>();
            List<String> fields = new ArrayList<>();
            if (inputHeader != null && !inputHeader.isEmpty()) {
                final TextUtils.StringSplitter COLUMN_SPLITTER = new TextUtils.SimpleStringSplitter('\t');
                COLUMN_SPLITTER.setString(inputHeader);
                int fieldCounter = 0;
                for (String columnName : COLUMN_SPLITTER) {
                    fields.add(columnName);
                    if (COLUMNS.contains(columnName)) {
                        header.put(fieldCounter, columnName);
                        if (HEADER_UTTERANCE.equals(columnName)) {
                            hasColumnUtterance = true;
                        }
                    }
                    fieldCounter++;
                }
            }
            mCommands = commands;
            // If the Utterance column is missing then assume that the
            // input was without a header and interpret it as a one or two column table.
            if (!hasColumnUtterance) {
                if (fields.size() > 1) {
                    mHeader = DEFAULT_HEADER_2;
                    mCommands.add(0, new Command(fields.get(0), fields.get(1)));
                } else if (fields.size() > 0) {
                    mHeader = DEFAULT_HEADER_1;
                    mCommands.add(0, new Command(fields.get(0), ""));
                } else {
                    mHeader = DEFAULT_HEADER_1;
                }
            } else {
                mHeader = header;
            }
        }

        public SortedMap<Integer, String> getHeader() {
            return mHeader;
        }

        public String getHeaderAsStr() {
            return TextUtils.join("\t", mHeader.values());
        }

        public List<Command> getCommands() {
            return mCommands;
        }

        public SortedMap<Integer, String> getErrors() {
            return mErrors;
        }

        private boolean addCommand(Command command) {
            return mCommands.add(command);
        }

        private String addError(int lineNumber, String message) {
            return mErrors.put(lineNumber, message);
        }

        public int size() {
            return mCommands.size();
        }

        /**
         * Adds a line unless it consists entirely of 0 or more tabs, or starts with "#".
         */
        public void addLine(String line, int lineCounter, CommandMatcher commandMatcher) {
            if (PATTERN_EMPTY_ROW.matcher(line).lookingAt()) {
                return;
            }
            try {
                Command command = getCommand(getHeader(), line, commandMatcher);
                if (command != null) {
                    addCommand(command);
                }
            } catch (PatternSyntaxException e) {
                addError(lineCounter, e.getLocalizedMessage());
            } catch (IllegalArgumentException e) {
                addError(lineCounter, e.getLocalizedMessage());
            }
        }
    }

    private final CommandHolder mCommandHolder;

    public UtteranceRewriter(List<Command> commands) {
        mCommandHolder = new CommandHolder(DEFAULT_HEADER_2, commands);
    }

    public UtteranceRewriter(List<Command> commands, String header) {
        mCommandHolder = new CommandHolder(header, commands);
    }

    public UtteranceRewriter(List<Command> commands, SortedMap<Integer, String> header) {
        mCommandHolder = new CommandHolder(header, commands);
    }

    public UtteranceRewriter(String str, CommandMatcher commandMatcher) {
        mCommandHolder = loadRewrites(str, commandMatcher);
    }

    public UtteranceRewriter(String str, String header, CommandMatcher commandMatcher) {
        mCommandHolder = loadRewrites(str, header, commandMatcher);
    }

    public UtteranceRewriter(String str, String header) {
        this(str, header, null);
    }

    /**
     * Loads the rewrites table from the given string. The header is detected automatically.
     * Command matching is not performed.
     *
     * @param str
     */
    public UtteranceRewriter(String str) {
        this(str, (CommandMatcher) null);
    }

    public UtteranceRewriter(ContentResolver contentResolver, Uri uri) throws IOException {
        mCommandHolder = loadRewrites(contentResolver, uri);
    }

    /**
     * Rewrites and returns the given string,
     * and the first matching command.
     */
    public Rewrite getRewrite(String str) {
        for (Command command : mCommandHolder.getCommands()) {
            Pair<String, String[]> pair = command.parse(str);
            String commandId = command.getId();
            if (commandId == null || commandId.isEmpty()) {
                str = pair.first;
            } else if (pair.second != null) {
                // If there is a full match (pair.second != null) and there is a command (commandId != null)
                // then stop the search and return the command.
                str = pair.first;
                return new Rewrite(commandId, str, pair.second, command);
            }
        }
        return new Rewrite(str);
    }

    @NonNull
    public List<Command> getCommands() {
        return mCommandHolder.getCommands();
    }

    /**
     * Rewrites and returns the given results.
     */
    public List<String> rewrite(List<String> results) {
        List<String> rewrittenResults = new ArrayList<>();
        for (String result : results) {
            rewrittenResults.add(getRewrite(result).mStr);
        }
        return rewrittenResults;
    }

    /**
     * Serializes the rewrites as tab-separated-values.
     */
    public String toTsv() {
        StringBuilder stringBuilder = new StringBuilder();
        SortedMap<Integer, String> header = mCommandHolder.getHeader();
        stringBuilder.append(mCommandHolder.getHeaderAsStr());
        for (Command command : mCommandHolder.getCommands()) {
            stringBuilder.append('\n');
            stringBuilder.append(command.toTsv(header));
        }
        return stringBuilder.toString();
    }

    public CommandHolder getCommandHolder() {
        return mCommandHolder;
    }

    public String[] getErrorsAsStringArray() {
        SortedMap<Integer, String> errors = mCommandHolder.getErrors();
        String[] array = new String[errors.size()];
        int i = 0;
        for (SortedMap.Entry<Integer, String> entry : errors.entrySet()) {
            array[i++] = "#" + entry.getKey() + ": " + entry.getValue();
        }
        return array;
    }

    /**
     * Loads the rewrites from a string of tab-separated values,
     * guessing the header from the string itself.
     */
    private static CommandHolder loadRewrites(String str, CommandMatcher commandMatcher) {
        if (str == null) {
            return new CommandHolder();
        }
        String[] rows = str.split(RE_LINE_SEPARATOR);
        int length = rows.length;
        if (length == 0) {
            return new CommandHolder();
        }
        CommandHolder commandHolder = new CommandHolder(rows[0]);
        if (length > 1) {
            for (int i = 1; i < length; i++) {
                commandHolder.addLine(rows[i], i, commandMatcher);
            }
        }
        return commandHolder;
    }

    /**
     * Loads the rewrites from a string of tab-separated values.
     * The header is given by a separate argument, the table must not
     * contain the header.
     */
    private static CommandHolder loadRewrites(String str, String header, CommandMatcher commandMatcher) {
        CommandHolder commandHolder = new CommandHolder(header);
        String[] rows = str.split(RE_LINE_SEPARATOR);
        if (rows.length > 0) {
            for (int i = 0; i < rows.length; i++) {
                commandHolder.addLine(rows[i], i, commandMatcher);
            }
        }
        return commandHolder;
    }

    /**
     * Loads the rewrites from an URI using a ContentResolver.
     * The first line is a header.
     * Non-header lines are ignored if they start with '#'.
     */
    private static CommandHolder loadRewrites(ContentResolver contentResolver, Uri uri) throws IOException {
        CommandHolder commandHolder = null;
        InputStream inputStream = contentResolver.openInputStream(uri);
        if (inputStream != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line = reader.readLine();
            if (line != null) {
                int lineCounter = 0;
                commandHolder = new CommandHolder(line);
                while ((line = reader.readLine()) != null) {
                    lineCounter++;
                    commandHolder.addLine(line, lineCounter, null);
                }
            }
            inputStream.close();
        }
        if (commandHolder == null) {
            return new CommandHolder();
        }
        return commandHolder;
    }

    /**
     * Creates a command based on the given fields.
     * For some fields the whitespace is trimmed from the beginning and end.
     *
     * @param header         parsed header
     * @param line           single row
     * @param commandMatcher command matcher
     * @return command or null if commandMatcher rejects the command
     */
    private static Command getCommand(SortedMap<Integer, String> header, String line, CommandMatcher commandMatcher) {
        String label = null;
        String comment = null;
        Pattern locale = null;
        Pattern service = null;
        Pattern app = null;
        Pattern utterance = null;
        String command = null;
        String replacement = null;
        String arg1 = null;
        String arg2 = null;

        final TextUtils.StringSplitter columnSplitter = new TextUtils.SimpleStringSplitter('\t');
        columnSplitter.setString(line);

        int i = 0;
        for (String split : columnSplitter) {
            String colName = header.get(i++);
            if (colName == null) {
                continue;
            }
            switch (colName) {
                case HEADER_LABEL:
                    label = split;
                    break;
                case HEADER_COMMENT:
                    comment = split.trim();
                    break;
                case HEADER_LOCALE:
                    locale = Pattern.compile(split.trim());
                    break;
                case HEADER_SERVICE:
                    service = Pattern.compile(split.trim());
                    break;
                case HEADER_APP:
                    app = Pattern.compile(split.trim());
                    break;
                case HEADER_UTTERANCE:
                    split = split.trim();
                    if (split.isEmpty()) {
                        throw new IllegalArgumentException("Empty Utterance");
                    }
                    utterance = Pattern.compile(split, Constants.REWRITE_PATTERN_FLAGS);
                    break;
                case HEADER_REPLACEMENT:
                    replacement = Command.unescape(split);
                    break;
                case HEADER_COMMAND:
                    command = Command.unescape(split.trim());
                    break;
                case HEADER_ARG1:
                    arg1 = Command.unescape(split);
                    break;
                case HEADER_ARG2:
                    arg2 = Command.unescape(split);
                    break;
                default:
                    // Columns with undefined names are ignored
                    break;
            }
        }

        if (commandMatcher != null && !commandMatcher.matches(locale, service, app)) {
            return null;
        }

        if (arg1 == null) {
            return new Command(label, comment, locale, service, app, utterance, replacement, command);
        }

        if (arg2 == null) {
            return new Command(label, comment, locale, service, app, utterance, replacement, command, new String[]{arg1});
        }

        return new Command(label, comment, locale, service, app, utterance, replacement, command, new String[]{arg1, arg2});
    }
}