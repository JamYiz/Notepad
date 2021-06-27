//编辑笔记内容的 Activity
package com.example.android.notepad;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;

public class NoteEditor extends Activity {

    // 用于记录和调试目的

    private static final String TAG = "NoteEditor";

    //创建一个返回笔记 ID 和笔记内容的投影。

    private static final String[] PROJECTION =
        new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE
    };

    // 活动保存状态的标签

    private static final String ORIGINAL_CONTENT = "origContent";

    // 此活动可以由多个操作启动。每个动作都表示为一个“状态”常量

    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private String mOriginalContent;

    // 定义一个自定义 EditText 视图，该视图在显示的每行文本之间绘制线条。

    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        // 此构造函数由 LayoutInflater 使用
        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            // 创建一个 Rect 和一个 Paint 对象，并设置 Paint 对象的样式和颜色。
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x800000FF);
        }

         //画笔与画图区域的初始化
        @Override
        protected void onDraw(Canvas canvas) {

            // 获取视图中文本的行数。
            int count = getLineCount();

            // 获取全局 Rect 和 Paint 对象
            Rect r = mRect;
            Paint paint = mPaint;


           //为 EditText 中的每一行文本在矩形中绘制一行

            for (int i = 0; i < count; i++) {

                // 获取当前文本行的基线坐标
                int baseline = getLineBounds(i, r);
                // 在每一行的下方绘制一条直线
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            super.onDraw(canvas);
        }
    }


    // 从传入的 Intent 中，它确定需要什么样的编辑，然后进行编辑。intent 参数获得 mUri，查询得到 mCursor。
    // onCreate 函数获取了 Intent 中的 Action 和 URI，随后根据不同 Action，进行不同的操作。

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        // 根据为传入 Intent 指定的操作设置编辑。
        // 获取触发此 Activity 的意图过滤器的操作
        final String action = intent.getAction();

        // 对于编辑操作：
        if (Intent.ACTION_EDIT.equals(action))
            {

            // 将 Activity 状态设置为 EDIT，并获取要编辑的数据的 URI。
            mState = STATE_EDIT;
            mUri = intent.getData();
            Log.d("zzz","uri="+mUri);
            // 对于插入或粘贴操作：
            }
        else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action))
            {

            // 设置Activity状态为INSERT，获取通用笔记URI，在provider中插入空记录
            mState = STATE_INSERT;
            mUri = getContentResolver().insert(intent.getData(), null);

            //如果尝试插入新笔记失败，则关闭此活动。
            // 如果原始 Activity 请求结果，则它会收到 RESULT_CANCELED。记录插入失败。
            if (mUri == null) {
                // 写入失败的日志标识符、消息和 URI。
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());
                finish();
                return;
            }

            // 由于创建了新条目，这将设置要返回的结果
            // 设置要返回的结果。
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        // 如果操作不是 EDIT 或 INSERT：
            }
        else {

            // 记录操作未被理解的错误，完成活动，并
            // 将 RESULT_CANCELED 返回到原始活动。
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
            }

//   使用与触发 Intent 一起传入的 URI，获取提供者中的一个或多个注释。

        mCursor = managedQuery(
                //从提供程序获取多个注释的 URI。
            mUri,
                // 返回每个笔记的笔记 ID 和笔记内容的投影。
            PROJECTION,
            null,
            null,
            null
        );

        // 对于粘贴，从剪贴板初始化数据。
        // （必须在 mCursor 初始化后完成。）
        if (Intent.ACTION_PASTE.equals(action)) {
            performPaste();
            // 将状态切换到 EDIT，以便可以修改标题。
            mState = STATE_EDIT;
        }

        // 设置此活动的布局。参见 res/layout/note_editor.xml
        setContentView(R.layout.note_editor);

        // 获取布局中 EditText 的句柄。
        mText = (EditText) findViewById(R.id.note);

        // 如果此 Activity 先前已停止，则其状态将写入保存的实例状态中的 ORIGINAL_CONTENT 位置。得到状态。
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
    }

    // 设置 title 及 edittext 中的文本
    // 当处于 EDIT (编辑) 和 INSERT (插入) 状态时，NoteEditor 显示的标题不同。

    @Override
    protected void onResume() {
        super.onResume();
        if (mCursor != null) {
            // 重新查询以防暂停时发生更改（例如标题）
            mCursor.requery();
            //移动到第一条记录。在第一次访问 Cursor 中的数据之前，始终调用 moveToFirst()。
            mCursor.moveToFirst();
            // 根据当前 Activity 状态修改 Activity 的窗口标题。
            // EDIT (编辑) 和 INSERT (插入) 状态时，NoteEditor 显示的标题不同。
            if (mState == STATE_EDIT) {
                // 设置 Activity 的标题以包含笔记标题
                int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                String title = mCursor.getString(colTitleIndex);
                Resources res = getResources();
                String text = String.format(res.getString(R.string.title_edit), title);
                setTitle(text);
            // 将插入的标题设置为“创建”
            } else if (mState == STATE_INSERT) {
                setTitle(getText(R.string.title_create));
            }
            // 从 Cursor 获取注释文本并将其放入 TextView，但不更改文本光标的位置。
            int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
            String note = mCursor.getString(colNoteIndex);
            mText.setTextKeepState(note);
            // 存储原始注释文本，以允许用户还原更改。
            if (mOriginalContent == null) {
                mOriginalContent = note;
            }
        /*
         *发生错误。光标应始终包含数据。在注释中报告错误。
         */
        } else {
            setTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
        }
    }

//活动结束前保存最初文本。
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }


    @Override
    protected void onPause() {
        super.onPause();

//测试以查看查询操作没有失败。
// Cursor 对象将存在，即使没有返回记录，除非查询因某些异常或错误而失败。
        if (mCursor != null) {
            // 获取当前笔记文本。
            String text = mText.getText().toString();
            int length = text.length();
//如果 Activity 正在完成中，并且当前笔记中没有文本，则返回 CANCELED 结果给调用者，并删除该笔记。
// 即使正在编辑注释，也会这样做，假设用户想要“清除”（删除）注释。
            if (isFinishing() && (length == 0)) {
                setResult(RESULT_CANCELED);
                deleteNote();
//将编辑内容写入提供程序。
// 如果在编辑器中检索了现有注释或插入了新注释，则注释已被编辑。
// 在后一种情况下，onCreate() 将一个新的空便笺插入到提供程序中，并且正在编辑的正是这个新便笺。
            } else if (mState == STATE_EDIT) {
                // 创建一个映射以包含列的新值
                updateNote(text, null);
            } else if (mState == STATE_INSERT) {
                updateNote(text, text);
                mState = STATE_EDIT;
          }
        }
    }


// 当用户第一次为此活动单击设备的菜单按钮时，将调用此方法。
// Android 传入一个填充了项目的 Menu 对象。构建用于编辑和插入的菜单，并添加注册自己以处理此应用程序的 MIME 类型的替代操作。
// @param menu 应添加项目的 Menu 对象。 @return True 显示菜单。

    //显示 editor_options_menue 中的内容为菜单内容

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 从 XML 资源拓展菜单
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);
        // 只为保存的笔记添加额外的菜单项
        if (mState == STATE_EDIT) {
            //附加到菜单项的任何其他活动，也可以用它做的事情。
            // 这会在系统上查询为我们的数据实现 ALTERNATIVE_ACTION 的任何活动，为找到的每个活动添加一个菜单项。
            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                    new ComponentName(this, NoteEditor.class), null, intent, 0, null);
        }

        return super.onCreateOptionsMenu(menu);
    }
    //根据内容是否改变决定 revert 菜单项是否显示

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
        String savedNote = mCursor.getString(colNoteIndex);
        String currentNote = mText.getText().toString();
        if (savedNote.equals(currentNote)) {
            menu.findItem(R.id.menu_revert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_revert).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    //菜单项的交互

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_save:
            String text = mText.getText().toString();
            updateNote(text, null);
            finish();
            break;
        case R.id.menu_delete:
            deleteNote();
            finish();
            break;
        case R.id.menu_revert:
            cancelNote();
            break;
        default:{
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

//一种用剪贴板内容替换笔记数据的辅助方法。
// 这个是剪切操作的实现，如果剪切的是 Note，就查询，获取 text,tilte，如果文本，直接作为 text，然后调用 updateNote () 函数保存更改。

    private void performPaste() {
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        ContentResolver cr = getContentResolver();

        // 从剪贴板中获取剪贴板数据
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {

            String text=null;
            String title=null;

            // 从剪贴板数据中获取第一项
            ClipData.Item item = clip.getItemAt(0);

            // 尝试将项目的内容作为指向笔记的 URI 获取
            Uri uri = item.getUri();

            // 测试以查看该项目实际上是一个 URI，并且该 URI
            // 是指向 MIME 类型相同的提供者的内容 URI
            // 作为记事本提供商支持的 MIME 类型。
            if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {
                // 剪贴板包含对具有注释 MIME 类型的数据的引用。这将复制它。
                Cursor orig = cr.query(
                        uri,
                        PROJECTION,
                        null,
                        null,
                        null
                );

                if (orig != null) {
                    if (orig.moveToFirst()) {
                        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                        int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                        text = orig.getString(colNoteIndex);
                        title = orig.getString(colTitleIndex);
                    }
                    orig.close();
                }
            }

            //如果剪贴板的内容不是对笔记的引用，那么这会将其转换为文本。
            if (text == null) {
                text = item.coerceToText(this).toString();
            }

            // 使用检索到的标题和文本更新当前注释。
            updateNote(text, title);
        }
    }
//END INCLUDE（粘贴）

    //updateNote 函数，如果 title 存在，将 text,title 存入，否则取前 30 字符为 title。

    private void updateNote(String text, String title) {

        // 设置映射以包含要在提供程序中更新的值。
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

        // 如果操作是插入新笔记，则会为其创建初始标题。
        if (mState == STATE_INSERT) {

            // 如果没有提供标题作为参数，则从注释文本创建一个。
            if (title == null) {
  
                // 获取笔记的长度
                int length = text.length();

                // 通过获取 31 个字符长的文本子字符串来设置标题
                // 或注释中的字符数加一，以较小者为准。
                title = text.substring(0, Math.min(30, length));
  
                // 如果结果长度超过 30 个字符，则去掉所有尾随空格
                if (length > 30) {
                    int lastSpace = title.lastIndexOf(' ');
                    if (lastSpace > 0) {
                        title = title.substring(0, lastSpace);
                    }
                }
            }
            // 在值映射中，设置标题的值
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        } else if (title != null) {
            // 在值映射中，设置标题的值
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        }

        // 这会将所需的注释文本放入映射中。
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);

        getContentResolver().update(
                mUri,
                values,
                null,
                null
            );


    }

    //cancleNote 如果取消，就将 mOrigContent 存回去。

    private void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                //将原始笔记文本放回数据库
                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                getContentResolver().update(mUri, values, null, null);
            } else if (mState == STATE_INSERT) {
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    //deleteNote 删除一条 note，将 EditText 设为空白行。

    private void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
        }
    }
}
