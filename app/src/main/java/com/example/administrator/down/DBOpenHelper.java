package com.example.administrator.down;

/**
 * Created by Administrator on 2018/6/9.
 */

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 总结：
 * 1 继承自SQLiteOpenHelper
 * 2 完成构造方法
 */
public class DBOpenHelper extends SQLiteOpenHelper {
    private final static String DBName = "download.db";
    private final static int VERSION = 1;

    public DBOpenHelper(Context context) {
        super(context, DBName, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS filedownload(id integer primary key autoincrement, downpath varchar(100), threadid INTEGER, downlength INTEGER)");

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS filedownload");
        onCreate(db);
    }

}