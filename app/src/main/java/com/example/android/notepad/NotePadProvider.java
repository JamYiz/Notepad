//这是笔记本应用的 ContentProvider，也是整个应用的关键所在，是NotePad 的内容提供者
package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.ContentProvider.PipeDataWriter;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.LiveFolders;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

/*
 提供对notes数据库的访问。每个note都有一个标题，note
 本身，一个创建日期和一个修改过的数据。
 */
public class NotePadProvider extends ContentProvider implements PipeDataWriter<Cursor> {
    // Used for debugging and logging
    private static final String TAG = "NotePadProvider";
    private static final String DATABASE_NAME = "note_pad.db";
    private static final int DATABASE_VERSION = 2;

    private static HashMap<String, String> sNotesProjectionMap;


    private static HashMap<String, String> sLiveFolderProjectionMap;


    private static final String[] READ_NOTE_PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_CREATE_DATE,
    };

    private static final int READ_NOTE_NOTE_INDEX = 1;
    private static final int READ_NOTE_TITLE_INDEX = 2;
    private static final int READ_NOTE_CREATE_DATE_INDEX = 3;


    private static final int NOTES = 1;

    private static final int NOTE_ID = 2;

    private static final int LIVE_FOLDER_NOTES = 3;


    private static final UriMatcher sUriMatcher;

    private DatabaseHelper mOpenHelper;


    static {

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes", NOTES);
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/#", NOTE_ID);
        sUriMatcher.addURI(NotePad.AUTHORITY, "live_folders/notes", LIVE_FOLDER_NOTES);
        sNotesProjectionMap = new HashMap<String, String>();
        sNotesProjectionMap.put(NotePad.Notes._ID, NotePad.Notes._ID);
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_TITLE);
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_NOTE, NotePad.Notes.COLUMN_NAME_NOTE);
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                NotePad.Notes.COLUMN_NAME_CREATE_DATE);
        sNotesProjectionMap.put(
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);

        sLiveFolderProjectionMap = new HashMap<String, String>();

        sLiveFolderProjectionMap.put(LiveFolders._ID, NotePad.Notes._ID + " AS " + LiveFolders._ID);

        sLiveFolderProjectionMap.put(LiveFolders.NAME, NotePad.Notes.COLUMN_NAME_TITLE + " AS " +
            LiveFolders.NAME);
    }

    /**
    *
     DatabaseHelper 是 SQLiteOpenHelper 的子类，用来辅助数据库的管理。
    */
   static class DatabaseHelper extends SQLiteOpenHelper {

       DatabaseHelper(Context context) {

           // calls the super constructor, requesting the default cursor factory.
           super(context, DATABASE_NAME, null, DATABASE_VERSION);
       }

       /**
        *
        * 使用取自 NotePad 类的表名和列名创建基础数据库.
        */
       @Override
       public void onCreate(SQLiteDatabase db) {
           db.execSQL("CREATE TABLE " + NotePad.Notes.TABLE_NAME + " ("
                   + NotePad.Notes._ID + " INTEGER PRIMARY KEY,"
                   + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT,"
                   + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT,"
                   + NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                   + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER"
                   + ");");
       }

       @Override
       public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

           Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                   + newVersion + ", which will destroy all old data");
           db.execSQL("DROP TABLE IF EXISTS notes");

           onCreate(db);
       }
   }

   /**
    *
    通过创建新的 DatabaseHelper 来初始化提供程序。
    当 Android 创建提供程序以响应来自客户端的解析器请求时，会自动调用 onCreate()。
    */
   @Override
   public boolean onCreate() {

       mOpenHelper = new DatabaseHelper(getContext());

       return true;
   }


    //查询记录时被调用
   @Override
   public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
           String sortOrder) {

       // 构造一个新的查询构建器并设置其表名
       SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
       qb.setTables(NotePad.Notes.TABLE_NAME);

        // 选择投影并根据 URI 模式匹配调整“where”子句。

       switch (sUriMatcher.match(uri)) {
           // 如果传入的 URI 用于笔记，则选择 Notes 投影
           case NOTES:
               qb.setProjectionMap(sNotesProjectionMap);
               break;

           /* 如果传入的 URI 是针对由其 ID 标识的单个笔记，则选择笔记 ID 投影，并将“_ID = <noteID>”附加到 where 子句，以便选择该单个笔记
            */
           case NOTE_ID:
               qb.setProjectionMap(sNotesProjectionMap);
               qb.appendWhere(
                   NotePad.Notes._ID +
                   "=" +
                   // 笔记 ID 本身在传入 URI 中的位置
                   uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION));
               break;

           case LIVE_FOLDER_NOTES:
               // 如果传入 URI 来自实时文件夹，则选择实时文件夹投影。
               qb.setProjectionMap(sLiveFolderProjectionMap);
               break;

           default:
               // 如果 URI 与任何已知模式都不匹配，则抛出异常。
               throw new IllegalArgumentException("Unknown URI " + uri);
       }


       String orderBy;
       // 如果未指定排序顺序，则使用默认值
       if (TextUtils.isEmpty(sortOrder)) {
           orderBy = NotePad.Notes.DEFAULT_SORT_ORDER;
       } else {
           // 否则，使用传入的排序顺序
           orderBy = sortOrder;
       }

       // Opens the database object in "read" mode, since no writes need to be done.
       SQLiteDatabase db = mOpenHelper.getReadableDatabase();

       Cursor c = qb.query(
           db,            // 要查询的数据库
           projection,    // 从查询返回的列
           selection,     // where 子句的列
           selectionArgs, // where 子句的值
           null,          // 不要对行进行分组
           null,          // 不要按行组过滤
           orderBy        // 排序顺序
       );

       // Tells the Cursor what URI to watch, so it knows when its source data changes
       c.setNotificationUri(getContext().getContentResolver(), uri);
       return c;
   }


   //获取 MIME 类型
   @Override
   public String getType(Uri uri) {

       switch (sUriMatcher.match(uri)) {

           // If the pattern is for notes or live folders, returns the general content type.
           case NOTES:
           case LIVE_FOLDER_NOTES:
               return NotePad.Notes.CONTENT_TYPE;

           // If the pattern is for note IDs, returns the note ID content type.
           case NOTE_ID:
               return NotePad.Notes.CONTENT_ITEM_TYPE;

           // If the URI pattern doesn't match any permitted patterns, throws an exception.
           default:
               throw new IllegalArgumentException("Unknown URI " + uri);
       }
    }

//BEGIN_INCLUDE(stream)
    /**
     * 这描述了支持将笔记 URI 作为流打开的 MIME 类型。
     */
    static ClipDescription NOTE_STREAM_TYPES = new ClipDescription(null,
            new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN });

    /**
     * 返回可用数据流的类型。支持特定笔记的 URI。应用程序可以将此类注释转换为纯文本流。 @param uri 要分析的 URI @param mimeTypeFilter 要检查的 MIME 类型。
     * 此方法仅返回与过滤器匹配的 MIME 类型的数据流类型。
     * 目前，只有 textplain MIME 类型匹配。 @return 数据流 MIME 类型。目前，只返回 textplan。 @throws IllegalArgumentException 如果 URI 模式不匹配任何支持的模式。
     */
    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        /**
         *  根据传入的 URI 模式选择数据流类型。
         */
        switch (sUriMatcher.match(uri)) {

            // 如果模式用于笔记或活动文件夹，则返回 null。此类 URI 不支持数据流。
            case NOTES:
            case LIVE_FOLDER_NOTES:
                return null;

            // 如果模式用于笔记 ID 并且 MIME 过滤器是 textplain，则返回 textplain
            case NOTE_ID:
                return NOTE_STREAM_TYPES.filterMimeTypes(mimeTypeFilter);

                // If the URI pattern doesn't match any permitted patterns, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
            }
    }



    @Override
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
            throws FileNotFoundException {

        //检查 MIME 类型过滤器是否与支持的 MIME 类型匹配。
        String[] mimeTypes = getStreamTypes(uri, mimeTypeFilter);

        // 如果支持 MIME 类型
        if (mimeTypes != null) {

            // 检索此 URI 的注释。使用为此提供程序定义的查询方法，而不是使用数据库查询方法。
            Cursor c = query(
                    uri,                    // 笔记的 URI
                    READ_NOTE_PROJECTION,   // 获取包含笔记 ID、标题、
                                            // 和内容
                    null,                   // 没有 WHERE 子句，获取所有匹配的记录
                    null,                   // 由于没有 WHERE 子句，因此没有选择标准
                    null                    //使用默认排序顺序（修改日期、
                                            // 下降
            );


            // 如果查询失败或游标为空，则停止
            if (c == null || !c.moveToFirst()) {

                // 如果游标为空，只需关闭游标并返回
                if (c != null) {
                    c.close();
                }

                //如果游标为空，则抛出异常
                throw new FileNotFoundException("Unable to query " + uri);
            }

            // 启动一个新线程，将流数据通过管道返回给调用者。
            return new AssetFileDescriptor(
                    openPipeHelper(uri, mimeTypes[0], opts, c, this), 0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }

        // 如果不支持 MIME 类型，则返回文件的只读句柄。
        return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
    }


    @Override
    public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType,
            Bundle opts, Cursor c) {
        FileOutputStream fout = new FileOutputStream(output.getFileDescriptor());
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(fout, "UTF-8"));

            pw.println(c.getString(READ_NOTE_TITLE_INDEX));
            pw.println("");
            pw.println(c.getString(READ_NOTE_NOTE_INDEX));
            pw.println("");
            pw.println(c.getString(READ_NOTE_CREATE_DATE_INDEX));
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Ooops", e);
        } finally {
            c.close();
            if (pw != null) {
                pw.flush();
            }
            try {
                fout.close();
            } catch (IOException e) {
            }
        }
    }

    //插入记录时被调用
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        // Validates the incoming URI. Only the full provider URI is allowed for inserts.
        if (sUriMatcher.match(uri) != NOTES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // A map to hold the new record's values.
        ContentValues values;

        // If the incoming values map is not null, uses it for the new values.
        if (initialValues != null) {
            values = new ContentValues(initialValues);

        } else {
            // Otherwise, create a new value map
            values = new ContentValues();
        }

        // Gets the current system time in milliseconds
        Long now = Long.valueOf(System.currentTimeMillis());

        // If the values map doesn't contain the creation date, sets the value to the current time.
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_CREATE_DATE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
        }

        // If the values map doesn't contain the modification date, sets the value to the current
        // time.
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
        }

        // If the values map doesn't contain a title, sets the value to the default title.
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_TITLE) == false) {
            Resources r = Resources.getSystem();
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, r.getString(android.R.string.untitled));
        }

        // If the values map doesn't contain note text, sets the value to an empty string.
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_NOTE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
        }

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        // Performs the insert and returns the ID of the new note.
        long rowId = db.insert(
            NotePad.Notes.TABLE_NAME,        // The table to insert into.
            NotePad.Notes.COLUMN_NAME_NOTE,  // A hack, SQLite sets this column value to null
                                             // if values is empty.
            values                           // A map of column names, and the values to insert
                                             // into the columns.
        );

        // If the insert succeeded, the row ID exists.
        if (rowId > 0) {
            // Creates a URI with the note ID pattern and the new row ID appended to it.
            Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, rowId);

            // Notifies observers registered against this provider that the data changed.
            getContext().getContentResolver().notifyChange(noteUri, null);
            return noteUri;
        }

        // If the insert didn't succeed, then the rowID is <= 0. Throws an exception.
        throw new SQLException("Failed to insert row into " + uri);
    }


    //删除记录时被调用
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {

        // 以“write”模式打开数据库对象。
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String finalWhere;

        int count;

        // Does the delete based on the incoming URI pattern.
        switch (sUriMatcher.match(uri)) {

            // If the incoming pattern matches the general pattern for notes, does a delete
            // based on the incoming "where" columns and arguments.
            case NOTES:
                count = db.delete(
                        // The database table name
                    NotePad.Notes.TABLE_NAME,
                        // The incoming where clause column names
                    where,
                        // The incoming where clause values
                    whereArgs
                );
                break;

                // If the incoming URI matches a single note ID, does the delete based on the
                // incoming data, but modifies the where clause to restrict it to the
                // particular note ID.
            case NOTE_ID:
                /*
                 * Starts a final WHERE clause by restricting it to the
                 * desired note ID.
                 */
                finalWhere =
                        NotePad.Notes._ID +                              // The ID column name
                        " = " +                                          // test for equality
                        uri.getPathSegments().                           // the incoming note ID
                            get(NotePad.Notes.NOTE_ID_PATH_POSITION)
                ;

                // If there were additional selection criteria, append them to the final
                // WHERE clause
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                // Performs the delete.
                count = db.delete(
                    NotePad.Notes.TABLE_NAME,  // The database table name.
                    finalWhere,                // The final WHERE clause
                    whereArgs                  // The incoming where clause values.
                );
                break;

            // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /*Gets a handle to the content resolver object for the current context, and notifies it
         * that the incoming URI changed. The object passes this along to the resolver framework,
         * and observers that have registered themselves for the provider are notified.
         */
        getContext().getContentResolver().notifyChange(uri, null);

        // Returns the number of rows deleted.
        return count;
    }


    //更新记录时被调用
    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String finalWhere;

        // Does the update based on the incoming URI pattern
        switch (sUriMatcher.match(uri)) {

            // If the incoming URI matches the general notes pattern, does the update based on
            // the incoming data.
            case NOTES:

                // Does the update and returns the number of rows updated.
                count = db.update(
                    NotePad.Notes.TABLE_NAME, // The database table name.
                    values,                   // A map of column names and new values to use.
                    where,                    // The where clause column names.
                    whereArgs                 // The where clause column values to select on.
                );
                break;

            // If the incoming URI matches a single note ID, does the update based on the incoming
            // data, but modifies the where clause to restrict it to the particular note ID.
            case NOTE_ID:
                // From the incoming URI, get the note ID
                String noteId = uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION);

                /*
                 * Starts creating the final WHERE clause by restricting it to the incoming
                 * note ID.
                 */
                finalWhere =
                        NotePad.Notes._ID +                              // The ID column name
                        " = " +                                          // test for equality
                        uri.getPathSegments().                           // the incoming note ID
                            get(NotePad.Notes.NOTE_ID_PATH_POSITION)
                ;

                // If there were additional selection criteria, append them to the final WHERE
                // clause
                if (where !=null) {
                    finalWhere = finalWhere + " AND " + where;
                }


                // Does the update and returns the number of rows updated.
                count = db.update(
                    NotePad.Notes.TABLE_NAME, // The database table name.
                    values,                   // A map of column names and new values to use.
                    finalWhere,               // The final WHERE clause to use
                                              // placeholders for whereArgs
                    whereArgs                 // The where clause column values to select on, or
                                              // null if the values are in the where argument.
                );
                break;
            // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /*Gets a handle to the content resolver object for the current context, and notifies it
         * that the incoming URI changed. The object passes this along to the resolver framework,
         * and observers that have registered themselves for the provider are notified.
         */
        getContext().getContentResolver().notifyChange(uri, null);

        // Returns the number of rows updated.
        return count;
    }

    DatabaseHelper getOpenHelperForTest() {
        return mOpenHelper;
    }
}
