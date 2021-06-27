package com.example.android.notepad;

import android.net.Uri;
import android.provider.BaseColumns;

public final class NotePad {
    public static final String AUTHORITY = "com.google.provider.NotePad";

    private NotePad() {
    }

    /**
     * Notes table contract
     */

    public static final class Notes implements BaseColumns {

        private Notes() {}

        //表名
        public static final String TABLE_NAME = "notes";
        //笔记标题
        public static final String COLUMN_NAME_TITLE = "title";
        //笔记内容
        public static final String COLUMN_NAME_NOTE = "note";
        //笔记创建时间
        public static final String COLUMN_NAME_CREATE_DATE = "created";
        //笔记的修改时间
        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";

        //URI 定义

        /*
         * The scheme part for this provider's URI
         */
        private static final String SCHEME = "content://";

        // URI 的路径部分

        //Notes URI 的路径
        private static final String PATH_NOTES = "/notes";

        //Note ID URI 的路径
        private static final String PATH_NOTE_ID = "/notes/";

        //Live Folder URI 的路径
        private static final String PATH_LIVE_FOLDER = "/live_folders/notes";

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =  Uri.parse(SCHEME + AUTHORITY + PATH_NOTES);

        /**
         * 0-relative position of a note ID segment in the path part of a note ID URI
         */
        public static final int NOTE_ID_PATH_POSITION = 1;


        /**
         * 单个笔记的内容 URI 基础。调用者必须在此 Uri 中附加一个数字注释 ID 才能检索注释
         */
        public static final Uri CONTENT_ID_URI_BASE
            = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID);

        /**
         * 单个笔记的内容 URI 匹配模式，由其 ID 指定。使用它来匹配传入的 URI 或构造一个intent。
         */
        public static final Uri CONTENT_ID_URI_PATTERN
            = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID + "/#");

        /**
         *活动文件夹的笔记列表的内容 Uri 模式
         */
        public static final Uri LIVE_FOLDER_URI
            = Uri.parse(SCHEME + AUTHORITY + PATH_LIVE_FOLDER);


        /*
         * MIME 类型定义
         */

        /**
         * 提供笔记目录的 {@link CONTENT_URI} 的 MIME 类型。
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.note";

        /**
         * 单个 {@link CONTENT_URI} 子目录的 MIME 类型
         * note.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.note";

        /**
         * 此表的默认排序顺序
         */
        public static final String DEFAULT_SORT_ORDER = "modified DESC";


    }
}
