package com.test.gang.proxy.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.test.gang.proxy.Preconditions
import com.test.gang.proxy.SourceInfo

/**
 * Database based [SourceInfoStorage].
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
internal class DatabaseSourceInfoStorage(context: Context?) :
    SQLiteOpenHelper(context, "AndroidVideoCache.db", null, 1),
    SourceInfoStorage {
    override fun onCreate(db: SQLiteDatabase) {
        Preconditions.checkNotNull(db)
        db.execSQL(CREATE_SQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("Should not be called. There is no any migration")
    }

    override operator fun get(url: String): SourceInfo? {
        Preconditions.checkNotNull(url)
        var cursor: Cursor? = null
        return try {
            cursor = readableDatabase.query(
                TABLE,
                ALL_COLUMNS,
                "$COLUMN_URL=?",
                arrayOf(url),
                null,
                null,
                null
            )
            if (cursor == null || !cursor.moveToFirst()) null else convert(cursor)
        } finally {
            cursor?.close()
        }
    }

    override fun put(url: String, sourceInfo: SourceInfo) {
        Preconditions.checkAllNotNull(url, sourceInfo)
        val sourceInfoFromDb: SourceInfo? = get(url)
        val exist = sourceInfoFromDb != null
        val contentValues: ContentValues = convert(sourceInfo)
        if (exist) {
            writableDatabase.update(TABLE, contentValues, "$COLUMN_URL=?", arrayOf(url))
        } else {
            writableDatabase.insert(TABLE, null, contentValues)
        }
    }

    override fun release() {
        close()
    }

    private fun convert(cursor: Cursor): SourceInfo {
        return SourceInfo(
            cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL)),
            cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LENGTH)),
            cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MIME))
        )
    }

    private fun convert(sourceInfo: SourceInfo): ContentValues {
        val values = ContentValues()
        values.put(COLUMN_URL, sourceInfo.url)
        values.put(COLUMN_LENGTH, sourceInfo.length)
        values.put(COLUMN_MIME, sourceInfo.mime)
        return values
    }

    companion object {
        private const val TABLE = "SourceInfo"
        private const val COLUMN_ID = "_id"
        private const val COLUMN_URL = "url"
        private const val COLUMN_LENGTH = "length"
        private const val COLUMN_MIME = "mime"
        private val ALL_COLUMNS = arrayOf(COLUMN_ID, COLUMN_URL, COLUMN_LENGTH, COLUMN_MIME)
        private const val CREATE_SQL = "CREATE TABLE " + TABLE + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                COLUMN_URL + " TEXT NOT NULL," +
                COLUMN_MIME + " TEXT," +
                COLUMN_LENGTH + " INTEGER" +
                ");"
    }

    init {
        Preconditions.checkNotNull(context)
    }
}