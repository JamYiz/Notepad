//编辑笔记标题的 Activity
package com.example.android.notepad;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

public class TitleEditor extends Activity {

    //创建一个返回笔记 ID 和笔记内容的投影。

    private static final String[] PROJECTION = new String[] {
            // 0
            NotePad.Notes._ID,
            // 1
            NotePad.Notes.COLUMN_NAME_TITLE,
    };

    // 提供者返回的 Cursor 中标题列的位置。

    private static final int COLUMN_INDEX_TITLE = 1;

    private Cursor mCursor;

    // 一个 EditText 对象，用于保留编辑过的标题。

    private EditText mText;

    // 正在编辑标题的笔记的 URI 对象。

    private Uri mUri;

//这个方法是Android在Activity第一次启动的时候调用的。从传入的 Intent 中，它确定需要什么样的编辑，然后进行编辑。

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 使用布局文件
        setContentView(R.layout.title_editor);
        //获取intent参数
        mUri = getIntent().getData();

        Log.d("hhh",mUri+"");
        mCursor = managedQuery(
            // 要检索的笔记的 URI。
            mUri,
            // 要检索的列
            PROJECTION,
            null,
            null,
            null
        );

        // 获取 EditText 框的视图 ID
        mText = (EditText) this.findViewById(R.id.title);
    }


    @Override
    protected void onResume() {
        super.onResume();
        // 验证在 onCreate() 中进行的查询是否确实有效。如果它有效，那么
        // 游标对象不为空。如果为空，则 mCursor.getCount() == 0。
        if (mCursor != null) {
            //这里表示读取第一条数据
            mCursor.moveToFirst();
            // 显示 EditText 对象中的当前标题文本。
            mText.setText(mCursor.getString(COLUMN_INDEX_TITLE));
        }
    }

//活动被隐藏时调用，即切屏切到另外一个程序或切到主界面时调用的。

    @Override
    protected void onPause() {
        super.onPause();

        // 验证在 onCreate() 中进行的查询是否确实有效。如果有效，则 Cursor 对象不为空。如果为空，则 mCursor.getCount() == 0。

        if (mCursor != null) {

            // 创建用于更新提供程序的值映射。
            ContentValues values = new ContentValues();

            // 在值映射中，将标题设置为编辑框的当前内容。
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, mText.getText().toString());

            getContentResolver().update(
                    // 要更新的笔记的 URI。
                mUri,
                    // 值映射包含要更新的列和要使用的值。
                values,
                null,
                null
            );

        }
    }

    public void onClickOk(View v) {
        finish();
    }
}
