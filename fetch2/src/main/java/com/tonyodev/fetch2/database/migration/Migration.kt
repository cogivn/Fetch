package com.tonyodev.fetch2.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


abstract class Migration constructor(startVersion: Int, endVersion: Int) :
    Migration(startVersion, endVersion) {

    override fun migrate(database: SupportSQLiteDatabase) {

    }
}