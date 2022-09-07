package com.tonyodev.fetch2.database

import androidx.room.*
import androidx.room.OnConflictStrategy.IGNORE
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.COLUMN_TAG_ID
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.TABLE_NAME
import com.tonyodev.fetch2.database.DownloadDatabase.Companion.TABLE_TAG_NAME
import com.tonyodev.fetch2.database.join.TagWithDownloads

@Dao
abstract class TagDao {

    @Transaction
    @Query("SELECT * FROM $TABLE_TAG_NAME WHERE $COLUMN_TAG_ID=:tagId")
    abstract fun getDownloadsByTag(tagId: Int): TagWithDownloads?

    @Transaction
    @Query("SELECT * FROM $TABLE_TAG_NAME WHERE $COLUMN_TAG_ID IN (:tagIds)")
    abstract fun getDownloadsByTags(tagIds: List<Int>):List<TagWithDownloads>

    @Insert(onConflict = IGNORE)
    abstract fun addTag(tag: Tag): Long

    @Update
    abstract fun updateTag(tag: Tag)

    @Transaction
    open fun addOrUpdateTag(tag: Tag) {
        if (addTag(tag) == -1L) {
            updateTag(tag)
        }
    }
}