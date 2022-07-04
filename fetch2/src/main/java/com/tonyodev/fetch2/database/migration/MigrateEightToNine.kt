package com.tonyodev.fetch2.database.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_AUTO_RETRY_ATTEMPTS
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_AUTO_RETRY_MAX_ATTEMPTS
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_CREATED
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_DOWNLOADED
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_DOWNLOAD_ON_ENQUEUE
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_ENQUEUE_ACTION
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_ERROR
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_EXTRAS
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_FILE
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_GROUP
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_HEADERS
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_ID
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_IDENTIFIER
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_NAMESPACE
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_NETWORK_TYPE
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_PRIORITY
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_STATUS
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_TAG
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_TOTAL
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_URL
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.TABLE_NAME

class MigrateEightToNine : Migration(8, 9) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        database.execSQL(
            "CREATE TABLE $TABLE_NAME (" +
                    "$COLUMN_ID INTEGER NOT NULL, " +
                    "$COLUMN_NAMESPACE TEXT NOT NULL, " +
                    "$COLUMN_URL TEXT NOT NULL, " +
                    "$COLUMN_FILE TEXT NOT NULL, " +
                    "$COLUMN_GROUP INTEGER NOT NULL, " +
                    "$COLUMN_PRIORITY INTEGER NOT NULL, " +
                    "$COLUMN_HEADERS TEXT NOT NULL, " +
                    "$COLUMN_DOWNLOADED INTEGER NOT NULL, " +
                    "$COLUMN_TOTAL INTEGER NOT NULL, " +
                    "$COLUMN_STATUS INTEGER NOT NULL, " +
                    "$COLUMN_ERROR TEXT NOT NULL, " +
                    "$COLUMN_NETWORK_TYPE INTEGER NOT NULL, " +
                    "$COLUMN_CREATED INTEGER NOT NULL, " +
                    "$COLUMN_TAG TEXT, " +
                    "$COLUMN_ENQUEUE_ACTION INTEGER NOT NULL, " +
                    "$COLUMN_IDENTIFIER INTEGER NOT NULL, " +
                    "$COLUMN_DOWNLOAD_ON_ENQUEUE INTEGER NOT NULL, " +
                    "$COLUMN_EXTRAS TEXT NOT NULL, " +
                    "$COLUMN_AUTO_RETRY_MAX_ATTEMPTS INTEGER NOT NULL, " +
                    "$COLUMN_AUTO_RETRY_ATTEMPTS INTEGER NOT NULL, PRIMARY KEY($COLUMN_ID))"
        )
    }
}