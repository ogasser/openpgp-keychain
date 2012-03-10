package org.apg.provider;

import org.apg.ApgService;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

public class ApgServiceBlobDatabase extends SQLiteOpenHelper {
    
    private static final String TAG = "ApgServiceBlobDatabase";

    private static final int VERSION = 1;
    private static final String NAME = "apg_service_blob_data";
    private static final String TABLE = "data";
        
    public ApgServiceBlobDatabase(Context context) {
        super(context, NAME, null, VERSION);
        if(ApgService.LOCAL_LOGD) Log.d(TAG, "constructor called");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if(ApgService.LOCAL_LOGD) Log.d(TAG, "onCreate() called");
        db.execSQL("create table " + TABLE + " ( _id integer primary key autoincrement," +
        		"key text not null)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(ApgService.LOCAL_LOGD) Log.d(TAG, "onUpgrade() called");
        // no upgrade necessary yet
    }
    
    public Uri insert(ContentValues vals) {
        if(ApgService.LOCAL_LOGD) Log.d(TAG, "insert() called");
        SQLiteDatabase db = this.getWritableDatabase();
        long newId = db.insert(TABLE, null, vals);
        return ContentUris.withAppendedId(ApgServiceBlobProvider.CONTENT_URI, newId);
    }
    
    public Cursor query(String id, String key) {
        if(ApgService.LOCAL_LOGD) Log.d(TAG, "query() called");
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE, new String[] {"_id"},
                "_id = ? and key = ?", new String[] {id, key},
                null, null, null);
    }
}