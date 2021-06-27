//应用程序的入口，笔记本的首页面会显示笔记的列表
package com.example.android.notepad;
import com.example.android.notepad.NotePad;
import com.example.android.notepad.application.MyApplication;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

/**
 * Displays a list of notes. Will display notes from the {@link Uri}
 * provided in the incoming Intent if there is one, otherwise it defaults to displaying the
 * contents of the {@link NotePadProvider}.
 */

public class NotesList extends ListActivity implements View.OnClickListener {
    //搜索框控件实例
    private EditText et_Search;
    //搜索按钮实例
    private ImageView iv_searchnotes;
    private ListView lv_notesList;
    private NotesListAdapter adapter;
    //添加按钮
    private ImageView iv_addnotes;
    private LinearLayout ll_noteList;
    // 用于记录和调试
    private static final String TAG = "NotesList";

    //游标适配器所需的列
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_CREATE_DATE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
    };


    /** 标题列的索引 */
    private static final int COLUMN_INDEX_TITLE = 1;


    // 当 Android 从头启动此 Activity时，会调用onCreate。

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.noteslist_layout);
        initView();
        // 用户无需按住该键即可使用菜单快捷方式。
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
        Intent intent = getIntent();
        // 如果没有与 Intent 关联的数据，则将数据设置为默认 URI，该 URI 将访问笔记列表。
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        /*
         * 为 ListView 设置上下文菜单激活回调。侦听器设置为此 Activity。
         * 效果是为 ListView 中的项目启用了上下文菜单，并且上下文菜单由 NotesList 中的方法处理。
         */
        getListView().setOnCreateContextMenuListener(this);

        Cursor cursor = managedQuery(
            getIntent().getData(),
            PROJECTION,
            null,
            null,
            NotePad.Notes.DEFAULT_SORT_ORDER
        );

        adapter=new NotesListAdapter(getApplicationContext(),cursor,getIntent().getData(),getIntent().getAction());
        lv_notesList.setAdapter(adapter);

    }

    /*
    绑定id
     */
    private void initView() {
        ll_noteList= (LinearLayout) findViewById(R.id.noteList_layout);
        iv_addnotes= (ImageView) findViewById(R.id.fab);
        lv_notesList= (ListView) findViewById(android.R.id.list);//绑定listView;
        et_Search= (EditText) findViewById(R.id.et_Search);
        iv_searchnotes= (ImageView) findViewById(R.id.iv_searchnotes);

        iv_addnotes.setOnClickListener(this);
        iv_searchnotes.setOnClickListener(this);
        ll_noteList.setBackgroundColor(Color.parseColor(MyApplication.getBackground()));
        lv_notesList.setBackgroundColor(Color.parseColor(MyApplication.getBackground()));

    }

    //监听点击事件
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.fab:
                startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
                break;
            case R.id.iv_searchnotes:
                showOrhide();
                if(et_Search.getText().toString().equals("")){
                    Cursor cursor1 = managedQuery(
                            getIntent().getData(),
                            PROJECTION,
                            null,
                            null,
                            NotePad.Notes.DEFAULT_SORT_ORDER
                    );
                    adapter.readDate(cursor1);
                    adapter.notifyDataSetChanged();
                }else{
                    adapter.Search(et_Search.getText().toString());
                }
            default:{
                break;
            }

        }
    }

    /*
    软硬盘的显示和隐藏
     */
    private void showOrhide(){
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);
    }
    @Override
    protected void onRestart() {
        super.onRestart();
        Cursor cursor1 = managedQuery(
                // 使用提供者的默认内容 URI。
               getIntent().getData(),
                // 返回每个笔记的笔记 ID 和标题。
                PROJECTION,
                // 没有 where 子句，返回所有记录。
                null,
                // 没有 where 子句，因此没有 where 列值。
                null,
                // 使用默认排序顺序。
                NotePad.Notes.DEFAULT_SORT_ORDER
        );
        adapter.readDate(cursor1);
        adapter.notifyDataSetChanged();
    }


//    从 res/menu/list_options_menu 中获取菜单信息，填入菜单，将所有有关该 intent 的选项加进去
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        //生成可以在整个列表上执行的任何其他操作。
        // 在正常安装中，此处找不到其他操作，但这允许其他应用程序使用自己的操作扩展我们的菜单。
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        return super.onCreateOptionsMenu(menu);
    }

    //如果剪切板中有东西就加入 Edit，否则移除。
    //注意此函数在上面函数值后执行会覆盖上面对菜单的修改

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // 如果剪贴板上有数据，则启用粘贴菜单项。
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);


        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);

        // 如果剪贴板包含一个项目，则启用菜单上的粘贴选项。
        if (clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            // 如果剪贴板为空，则禁用菜单的粘贴选项。
            mPasteItem.setEnabled(false);
        }

        // 获取当前显示的笔记数。
        final boolean haveItems = adapter.getCount() > 0;

        // 如果列表中有任何注释（这意味着选择了其中之一），那么我们需要生成可以对当前选择执行的操作。
        // 这将是我们自己的特定操作以及可以找到的任何扩展的组合。
        if (haveItems) {

            // 这是选定的项目。
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            // 创建一个包含一个元素的 Intent 数组。这将用于根据所选菜单项发送意图。
            Intent[] specifics = new Intent[1];

            // 将数组中的 Intent 设置为所选笔记的 URI 上的 EDIT 操作。
            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

            // 创建一个包含一个元素的菜单项数组。这将包含 EDIT 选项。
            MenuItem[] items = new MenuItem[1];

            // 使用所选笔记的 URI 创建一个没有特定操作的 Intent。
            Intent intent = new Intent(null, uri);

            /* 将类别 ALTERNATIVE 添加到 Intent，并将笔记 ID URI 作为其数据。
                这将 Intent 准备好作为对菜单中的替代选项进行分组的地方。
             */
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            /*
             * 向菜单添加替代品
             */
            menu.addIntentOptions(
                Menu.CATEGORY_ALTERNATIVE,
                Menu.NONE,
                Menu.NONE,
                null,
                specifics,
                intent,
                Menu.NONE,
                items

            );
                // If the Edit menu item exists, adds shortcuts for it.
                if (items[0] != null) {

                    // Sets the Edit menu item shortcut to numeric "1", letter "e"
                    items[0].setShortcut('1', 'e');
                }
            } else {
                // If the list is empty, removes any existing alternative actions from the menu
                menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
            }

        // Displays the menu
        return true;
    }

//    根据 add 还是 paste 传递不同值给要启动的 Actitvity
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_paste:
          /*
           * Launches a new Activity using an Intent. The intent filter for the Activity
           * has to have action ACTION_PASTE. No category is set, so DEFAULT is assumed.
           * In effect, this starts the NoteEditor Activity in NotePad.
           */
          startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()));
          return true;
            case R.id.bg_change:
                showpopSelectBgWindows();
                return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

//    根据位置找到是哪个 note，修改菜单头部名字
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        // Tries to get the position of the item in the ListView that was long-pressed.
        try {
            // Casts the incoming data object into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            // If the menu object can't be cast, logs an error.
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        /*
         * Gets the data associated with the item at the selected position. getItem() returns
         * whatever the backing adapter of the ListView has associated with the item. In NotesList,
         * the adapter associated all of the data for a note with its list item. As a result,
         * getItem() returns that data as a Cursor.
         */

        Cursor cursor = managedQuery(
                Uri.parse(getIntent().getData()+"/"+adapter.getmDate().get(info.position).getCursor_id()),            // Use the default content URI for the provider.
                PROJECTION,                       // Return the note ID and title for each note.
                null,                             // No where clause, return all records.
                null,                             // No where clause, therefore no where column values.
                NotePad.Notes.DEFAULT_SORT_ORDER  // Use the default sort order.
        );
        // Cursor cursor = (Cursor) adapter.getItem(info.position);

        // If the cursor is empty, then for some reason the adapter can't get the data from the
        // provider, so returns null to the caller.
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }else{
            cursor.moveToNext();
        }
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // Sets the menu header to be the title of the selected note.
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        // Append to the
        // menu items for any other activities that can do stuff with it
        // as well.  This does a query on the system for any activities that
        // implement the ALTERNATIVE_ACTION for our data, adding a menu item
        // for each one that is found.
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                                        adapter.getmDate().get(info.position).getCursor_id()) );
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);


    }
//    获取 note id，根据菜单项启动相应 Actitvty,intent,uri
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        /*
         * Gets the extra info from the menu item. When an note in the Notes list is long-pressed, a
         * context menu appears. The menu items for the menu automatically get the data
         * associated with the note that was long-pressed. The data comes from the provider that
         * backs the list.
         *
         * The note's data is passed to the context menu creation routine in a ContextMenuInfo
         * object.
         *
         * When one of the context menu items is clicked, the same data is passed, along with the
         * note ID, to onContextItemSelected() via the item parameter.
         */
        try {
            // Casts the data object in the item into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {

            // If the object can't be cast, logs an error
            Log.e(TAG, "bad menuInfo", e);

            // Triggers default processing of the menu item.
            return false;
        }
        // Appends the selected note's ID to the URI sent with the incoming Intent.
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), Integer.parseInt(adapter.getmDate().get(info.position).getCursor_id()));
        /*
         * Gets the menu item's ID and compares it to known actions.
         */
        switch (item.getItemId()) {
            case R.id.context_open:
                // Launch activity to view/edit the currently selected item
                startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
                return true;
            //BEGIN_INCLUDE(copy)
            case R.id.context_copy:
                // Gets a handle to the clipboard service.
                ClipboardManager clipboard = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);

                // Copies the notes URI to the clipboard. In effect, this copies the note itself
                clipboard.setPrimaryClip(ClipData.newUri(   // new clipboard item holding a URI
                        getContentResolver(),               // resolver to retrieve URI info
                        "Note",                             // label for the clip
                        noteUri)                            // the URI
                );

                return true;

            default:
                return super.onContextItemSelected(item);
        }
    }


       //背景颜色选择框
    private  void showpopSelectBgWindows(){
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.dialog_bg_select_layout, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("背景");//设置标题
        builder.setView(view);
        AlertDialog dialog = builder.create();//获取dialog
        dialog.show();//显示对话框
    }


    //背景改变的监听
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void ColorSelect(View view){
        String color;
        switch(view.getId()){
            case R.id.pink:
                /*此处进行背景颜色修改,后改为主题效果*/
                Drawable btnDrawable1 = getResources().getDrawable(R.drawable.pink);
                ll_noteList.setBackground(btnDrawable1);
                lv_notesList.setBackground(btnDrawable1);

                break;
            case R.id.Yello:
                Drawable btnDrawable2 = getResources().getDrawable(R.drawable.yellow);
                ll_noteList.setBackground(btnDrawable2);
                lv_notesList.setBackground(btnDrawable2);
                break;
            case R.id.PaleVioletRed:
                Drawable btnDrawable3 = getResources().getDrawable(R.drawable.palevioletred);
                ll_noteList.setBackground(btnDrawable3);
                lv_notesList.setBackground(btnDrawable3);
                break;
            case R.id.LightGrey:
                Drawable btnDrawable4 = getResources().getDrawable(R.drawable.lightgrey);
                ll_noteList.setBackground(btnDrawable4);
                lv_notesList.setBackground(btnDrawable4);
                break;
            case R.id.MediumPurple:
                Drawable btnDrawable5 = getResources().getDrawable(R.drawable.mediumpurple);
                ll_noteList.setBackground(btnDrawable5);
                lv_notesList.setBackground(btnDrawable5);
                break;
            case R.id.DarkGray:
                Drawable btnDrawable6 = getResources().getDrawable(R.drawable.darkgray);
                ll_noteList.setBackground(btnDrawable6);
                lv_notesList.setBackground(btnDrawable6);
                break;
            case R.id.Snow:
                Drawable btnDrawable7 = getResources().getDrawable(R.drawable.snow);
                ll_noteList.setBackground(btnDrawable7);
                lv_notesList.setBackground(btnDrawable7);
                break;
            default:{

                break;
            }
        }


    }



}
