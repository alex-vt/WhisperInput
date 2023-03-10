package com.alexvt.whisperinput.speak.activity;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import java.net.MalformedURLException;
import java.net.URL;

import com.alexvt.whisperinput.R;
import com.alexvt.whisperinput.speak.fragment.K6neleListFragment;
import com.alexvt.whisperinput.speak.provider.Grammar;
import com.alexvt.whisperinput.speak.provider.Server;
import com.alexvt.whisperinput.speak.utils.Utils;

/**
 * <p>This activity lists all the grammar URLs and allows the
 * user to add more. The list data comes from the Grammar-table
 * via the standard SimpleCursorAdapter. Long-tapping on a
 * list item allows the user to:</p>
 *
 * <ul>
 * <li>view the content of the grammar (in a browser)</li>
 * <li>edit the entry, i.e. the name, URL, etc.</li>
 * <li>delete the entry</li>
 * </ul>
 *
 * @author Kaarel Kaljurand
 */
public class GrammarListActivity extends AbstractContentActivity {

    private static final Uri CONTENT_URI = Grammar.Columns.CONTENT_URI;
    private static final String SORT_ORDER = Grammar.Columns.NAME + " ASC";

    private static final String[] COLUMNS = new String[]{
            Grammar.Columns._ID,
            Grammar.Columns.NAME,
            Grammar.Columns.LANG,
            Grammar.Columns.DESC,
            Grammar.Columns.URL
    };

    private static final int[] TO = new int[]{
            R.id.itemGrammarId,
            R.id.itemGrammarName,
            R.id.itemGrammarLang,
            R.id.itemGrammarDesc,
            R.id.itemGrammarUrl
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GrammarListActivity.CursorLoaderListFragment fragment = new GrammarListActivity.CursorLoaderListFragment();
        fragment.setArguments(getIntent().getExtras());
        getSupportFragmentManager().beginTransaction().add(android.R.id.content, fragment).commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuGrammarsAdd:
                Utils.getTextEntryDialog(
                        this,
                        getString(R.string.dialogTitleNewGrammar),
                        getString(R.string.defaultUrlPrefix),
                        url -> {
                            if (url.length() > 0) {
                                try {
                                    new URL(url);
                                    String name = url.replaceFirst(".*\\/", "").replaceFirst("\\.[^.]*$", "");
                                    ContentValues values = new ContentValues();
                                    values.put(Grammar.Columns.NAME, name);
                                    values.put(Grammar.Columns.URL, url);
                                    insert(CONTENT_URI, values);
                                } catch (MalformedURLException e) {
                                    toast(getString(R.string.exceptionMalformedUrl));
                                }
                            }
                        }
                ).show();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.cm_grammar, menu);
    }

    private boolean menuAction(int itemId, Cursor cursor) {

        final long key = cursor.getLong(cursor.getColumnIndexOrThrow(Grammar.Columns._ID));
        String name = cursor.getString(cursor.getColumnIndexOrThrow(Grammar.Columns.NAME));
        String grammarName = cursor.getString(cursor.getColumnIndexOrThrow(Grammar.Columns.NAME));
        String grammarLang = cursor.getString(cursor.getColumnIndexOrThrow(Grammar.Columns.LANG));
        String grammarUrl = cursor.getString(cursor.getColumnIndexOrThrow(Grammar.Columns.URL));

        switch (itemId) {
            case R.id.cmGrammarView:
                Intent intentView = new Intent();
                intentView.setAction(Intent.ACTION_VIEW);
                intentView.setDataAndType(Uri.parse(grammarUrl), "text/plain");
                startActivity(intentView);
                return true;
            case R.id.cmGrammarEditName:
                Utils.getTextEntryDialog(
                        this,
                        getString(R.string.dialogTitleChangeGrammarName),
                        grammarName,
                        name1 -> {
                            if (name1 != null && name1.length() == 0) {
                                name1 = null;
                            }
                            update(CONTENT_URI, key, Grammar.Columns.NAME, name1);
                        }
                ).show();
                return true;
            case R.id.cmGrammarEditLang:
                Utils.getTextEntryDialog(
                        this,
                        getString(R.string.dialogTitleChangeGrammarLang),
                        grammarLang,
                        lang -> {
                            if (lang != null && lang.length() == 0) {
                                lang = null;
                            }
                            update(CONTENT_URI, key, Grammar.Columns.LANG, lang);
                        }
                ).show();
                return true;
            case R.id.cmGrammarEditUrl:
                Utils.getTextEntryDialog(
                        this,
                        getString(R.string.dialogTitleChangeGrammarUrl),
                        grammarUrl,
                        newUrl -> {
                            try {
                                updateUrl(CONTENT_URI, key, Grammar.Columns.URL, newUrl);
                            } catch (MalformedURLException e) {
                                toast(getString(R.string.exceptionMalformedUrl));
                            }
                        }
                ).show();
                return true;
            case R.id.cmGrammarDelete:
                Utils.getYesNoDialog(
                        this,
                        String.format(getString(R.string.confirmDeleteEntry), name),
                        () -> delete(CONTENT_URI, key)
                ).show();
                return true;
            default:
                return false;
        }
    }

    public static class CursorLoaderListFragment extends K6neleListFragment implements LoaderManager.LoaderCallbacks<Cursor> {
        private SimpleCursorAdapter mAdapter;

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            setEmptyText(getString(R.string.emptylistGrammars));
            setHasOptionsMenu(true);
            mAdapter = new SimpleCursorAdapter(
                    getActivity(),
                    R.layout.list_item_grammar,
                    null,
                    COLUMNS,
                    TO,
                    0
            );
            setListAdapter(mAdapter);
            registerForContextMenu(getListView());
            LoaderManager.getInstance(this).initLoader(0, null, this);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.grammars, menu);
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            Cursor cursor = (Cursor) l.getItemAtPosition(position);
            final long key = cursor.getLong(cursor.getColumnIndexOrThrow(Server.Columns._ID));
            ((AbstractContentActivity) getActivity()).returnIntent(CONTENT_URI, key);
        }

        public boolean onContextItemSelected(MenuItem item) {
            final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            Cursor cursor = (Cursor) getListView().getItemAtPosition(info.position);
            boolean success = ((GrammarListActivity) getActivity()).menuAction(item.getItemId(), cursor);
            return success || super.onContextItemSelected(item);
        }

        @NonNull
        @Override
        public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
            return new CursorLoader(getActivity(),
                    CONTENT_URI,
                    COLUMNS,
                    null,
                    null,
                    SORT_ORDER);
        }

        @Override
        public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
            mAdapter.swapCursor(data);
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Cursor> loader) {
            mAdapter.swapCursor(null);
        }
    }
}