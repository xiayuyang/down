package com.example.administrator.down;

/**
 * Created by Administrator on 2018/6/9.
 */

import java.util.HashMap;
import java.util.Map;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * 该类主要用来操作每条线程对应的保存在数据库中的下载信息
 * 比如:某条线程已经下载的数据量
 */

public class DownloadThreadHelper {

    private DBOpenHelper dbOpenHelper;

    public DownloadThreadHelper(Context context) {
        dbOpenHelper = new DBOpenHelper(context);
    }

    /**
     * 保存每条线程已经下载的长度
     */
    public void saveEveryThreadDownloadLength(String path, Map<Integer, Integer> map) {
        SQLiteDatabase db = dbOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
                db.execSQL("insert into filedownload(downpath, threadid, downlength) values(?,?,?)",
                        new Object[] { path, entry.getKey(), entry.getValue() });
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /**
     * 获取每条线程已经下载的长度
     */
    public Map<Integer, Integer> getEveryThreadDownloadLength(String path){
        SQLiteDatabase db = dbOpenHelper.getWritableDatabase();
        Cursor cursor=db.rawQuery("select threadid,downlength from filedownload where downpath=?", new String[]{path});
        Map<Integer, Integer> threadsMap=new HashMap<Integer, Integer>();
        while(cursor.moveToNext()){
            int threadid=cursor.getInt(0);
            int downlength=cursor.getInt(1);
            threadsMap.put(threadid, downlength);
        }
        cursor.close();
        db.close();
        return threadsMap;
    }

    /**
     * 实时更新每条线程已经下载的数据长度
     * 利用downPath和threadid来确定其已下载长度
     */
    public void updateEveryThreadDownloadLength(String path, Map<Integer, Integer> map) {
        SQLiteDatabase db = dbOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
                db.execSQL("update filedownload set downlength=? where threadid=? and downpath=?",new Object[] { entry.getValue(), entry.getKey(), path});
                System.out.println("更新该线程下载情况  threadID="+entry.getKey()+",length="+entry.getValue());
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /**
     * 下载完成后,删除每条线程的记录
     */
    public void deleteEveryThreadDownloadRecord(String path){
        SQLiteDatabase db = dbOpenHelper.getWritableDatabase();
        db.execSQL("delete from filedownload where downpath=?", new String[]{path});
        db.close();
    }
}