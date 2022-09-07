package com.tonyodev.fetch2.database


import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Priority
import com.tonyodev.fetch2.PrioritySort
import com.tonyodev.fetch2.Status
import com.tonyodev.fetch2.database.migration.Migration
import com.tonyodev.fetch2.database.models.ExtraUpdater
import com.tonyodev.fetch2.exception.FetchException
import com.tonyodev.fetch2.fetch.LiveSettings
import com.tonyodev.fetch2.util.defaultNoError
import com.tonyodev.fetch2core.DefaultStorageResolver
import com.tonyodev.fetch2core.Extras
import com.tonyodev.fetch2core.Logger


class FetchDatabaseManagerImpl constructor(
    context: Context,
    private val namespace: String,
    override val logger: Logger,
    migrations: Array<Migration>,
    private val liveSettings: LiveSettings,
    private val fileExistChecksEnabled: Boolean,
    private val defaultStorageResolver: DefaultStorageResolver
) : FetchDatabaseManager<DownloadInfo> {

    @Volatile
    private var closed = false
    override val isClosed: Boolean
        get() {
            return closed
        }
    override var delegate: FetchDatabaseManager.Delegate<DownloadInfo>? = null
    private val requestDatabase: DownloadDatabase
    private val database: SupportSQLiteDatabase

    init {
        val builder = Room.databaseBuilder(context, DownloadDatabase::class.java, "$namespace.db")
        builder.addMigrations(*migrations)
        builder.fallbackToDestructiveMigrationFrom(8, 9)
        requestDatabase = builder.build()
        database = requestDatabase.openHelper.writableDatabase
    }

    override fun insert(downloadInfo: DownloadInfo): Pair<DownloadInfo, Boolean> {
        throwExceptionIfClosed()
        val row = requestDatabase.requestDao().insert(downloadInfo)
        val wasRowInserted = requestDatabase.wasRowInserted(row)
        if (wasRowInserted) {
            logger.d("From insert(info/${downloadInfo.id}/$row) function -> Start insert/update tag ref.")
            executeInsertOrUpdateTag(downloadInfo)
        } else logger.e("Insert failed --> insert result = $row, info= $downloadInfo")
        return Pair(downloadInfo, requestDatabase.wasRowInserted(row))
    }

    override fun insert(downloadInfoList: List<DownloadInfo>): List<Pair<DownloadInfo, Boolean>> {
        throwExceptionIfClosed()
        val rowsList = requestDatabase.requestDao().insert(downloadInfoList)
        return rowsList.indices.map {
            val id = rowsList[it]
            val info = downloadInfoList[it]
            val wasRowInserted = requestDatabase.wasRowInserted(id)
            if (wasRowInserted) {
                logger.d("From insert(info(s)/${info.id}/$id) function -> Start insert/update tag ref.")
                executeInsertOrUpdateTag(info)
            } else logger.e("Insert failed --> insert result = $id, info= $info")
            Pair(info, wasRowInserted)
        }
    }

    override fun delete(downloadInfo: DownloadInfo, softDelete: Boolean) {
        throwExceptionIfClosed()
        if (softDelete) {
            val download = downloadInfo.apply { status = Status.DELETED }
            requestDatabase.requestDao().update(download)
        } else requestDatabase.requestDao().delete(downloadInfo)

    }

    override fun delete(downloadInfoList: List<DownloadInfo>, softDelete: Boolean) {
        throwExceptionIfClosed()
        if (softDelete) {
            val downloads = downloadInfoList.map { it.apply { status = Status.DELETED } }
            requestDatabase.requestDao().update(downloads)
        } else requestDatabase.requestDao().delete(downloadInfoList)

    }

    override fun deleteAll() {
        throwExceptionIfClosed()
        requestDatabase.requestDao().deleteAll()
        logger.d("Cleared Database $namespace")
    }

    override fun update(downloadInfo: DownloadInfo) {
        throwExceptionIfClosed()
        val wasRowExisted = requestDatabase.requestDao().isRowIsExist(downloadInfo.id)
        if (wasRowExisted) {
            requestDatabase.requestDao().update(downloadInfo)
            logger.d("From update(info/${downloadInfo.id}) function -> Start insert/update tag ref.")
            executeInsertOrUpdateTag(downloadInfo)
        } else logger.d("From update(info) --> result = Update failed with ${downloadInfo.id}")
    }

    override fun update(downloadInfoList: List<DownloadInfo>) {
        throwExceptionIfClosed()
        requestDatabase.requestDao().update(downloadInfoList)
        downloadInfoList.map {
            val wasRowExisted = requestDatabase.requestDao().isRowIsExist(it.id)
            if (wasRowExisted) {
                logger.d("From update(info(s)/${it.id}) function -> Start insert/update tag ref.")
                executeInsertOrUpdateTag(it)
            } else logger.d("From update(info(s)) --> result = Update failed with ${it.id}")
        }
    }

    override fun updateFileBytesInfoAndStatusOnly(downloadInfo: DownloadInfo) {
        throwExceptionIfClosed()
        try {
            database.beginTransaction()

            database.execSQL(
                "UPDATE ${DownloadDatabase.TABLE_NAME} SET "
                        + "${DownloadDatabase.COLUMN_DOWNLOADED} = ?, "
                        + "${DownloadDatabase.COLUMN_TOTAL} = ?, "
                        + "${DownloadDatabase.COLUMN_STATUS} = ? "
                        + "WHERE ${DownloadDatabase.COLUMN_ID} = ?",
                arrayOf(
                    downloadInfo.downloaded,
                    downloadInfo.total,
                    downloadInfo.status.value,
                    downloadInfo.id
                )
            )
            database.setTransactionSuccessful()
        } catch (e: SQLiteException) {
            logger.e("DatabaseManager exception", e)
        }
        try {
            database.endTransaction()
        } catch (e: SQLiteException) {
            logger.e("DatabaseManager exception", e)
        }
    }

    override fun updateExtras(id: Int, extras: Extras): DownloadInfo? {
        throwExceptionIfClosed()
        val newExtras = ExtraUpdater(id, extras)
        requestDatabase.requestDao().updateExtras(newExtras)
        val download = requestDatabase.requestDao().get(id)
        sanitize(download)
        return download
    }


    override fun deleteExtraByKey(id: Int, key: String): DownloadInfo? {
        throwExceptionIfClosed()
        val download = requestDatabase.requestDao().get(id)
            ?.apply { extras.removeByKey(key) }

        if (download != null) updateExtras(id, download.extras)
        sanitize(download)
        return download
    }

    override fun deleteExtraByKey(ids: List<Int>, key: String): List<DownloadInfo?> {
        throwExceptionIfClosed()
        val downloads = requestDatabase.requestDao().get(ids).map {
            deleteExtraByKey(it.download.id, key)
        }
        return downloads
    }

    override fun updatePriority(ids: List<Int>, priority: Priority): List<Download> {
        throwExceptionIfClosed()
        val downloads = requestDatabase.requestDao().get(ids).map {
            it.download.apply { this.priority = priority }
        }.also { update(it) }

        return downloads
    }

    override fun get(): List<DownloadInfo> {
        throwExceptionIfClosed()
        val downloadsWithTags = requestDatabase.requestDao().get()
        val downloads = downloadsWithTags.map {
            it.download.apply { tags = it.tags.map { tag -> tag.title } }
        }
        sanitize(downloads)
        return downloads
    }

    override fun get(id: Int): DownloadInfo? {
        throwExceptionIfClosed()
        val download = requestDatabase.requestDao().get(id)
        sanitize(download)
        return download
    }

    override fun get(ids: List<Int>): List<DownloadInfo?> {
        throwExceptionIfClosed()
        val downloadsWithTags = requestDatabase.requestDao().get(ids)
        val downloads = downloadsWithTags.map {
            it.download.apply { tags = it.tags.map { tag -> tag.title } }
        }
        sanitize(downloads)
        return downloads
    }

    override fun getByFile(file: String): DownloadInfo? {
        throwExceptionIfClosed()
        val downloadWithTag = requestDatabase.requestDao().getByFile(file)
        val download = downloadWithTag?.download?.apply {
            tags = downloadWithTag.tags.map { it.title }
        }
        sanitize(download)
        return download
    }

    override fun getByStatus(status: Status): List<DownloadInfo> {
        throwExceptionIfClosed()
        val downloadsWithTags = requestDatabase.requestDao().getByStatus(status)
        var downloads = downloadsWithTags.map {
            it.download.apply { tags = it.tags.map { tag -> tag.title } }
        }
        if (sanitize(downloads)) {
            downloads = downloads.filter { it.status == status }
        }
        return downloads
    }

    override fun getByStatus(statuses: List<Status>): List<DownloadInfo> {
        throwExceptionIfClosed()
        val downloadsWithTags = requestDatabase.requestDao().getByStatus(statuses)
        var downloads = downloadsWithTags.map {
            it.download.apply { tags = it.tags.map { tag -> tag.title } }
        }
        if (sanitize(downloads)) {
            downloads = downloads.filter { statuses.contains(it.status) }
        }
        return downloads
    }

    override fun getByGroup(group: Int): List<DownloadInfo> {
        throwExceptionIfClosed()
        val downloadsWithTags = requestDatabase.requestDao().getByGroup(group)
        val downloads = downloadsWithTags.map {
            it.download.apply { tags = it.tags.map { tag -> tag.title } }
        }
        sanitize(downloads)
        return downloads
    }

    override fun getByGroups(ids: List<Int>): List<DownloadInfo> {
        throwExceptionIfClosed()
        val downloadsWithTags = requestDatabase.requestDao().getByGroups(ids)
        val downloads = downloadsWithTags.map {
            it.download.apply { tags = it.tags.map { tag -> tag.title } }
        }
        sanitize(downloads)
        return downloads
    }

    override fun getDownloadsInGroupWithStatus(
        groupId: Int,
        statuses: List<Status>
    ): List<DownloadInfo> {
        throwExceptionIfClosed()
        val downloadsWithTags = requestDatabase.requestDao().getByGroupWithStatus(groupId, statuses)
        var downloads = downloadsWithTags.map {
            it.download.apply { tags = it.tags.map { tag -> tag.title } }
        }
        if (sanitize(downloads)) {
            downloads = downloads.filter { download ->
                statuses.any { it == download.status }
            }
        }
        return downloads
    }

    override fun getDownloadsByRequestIdentifier(identifier: Long): List<DownloadInfo> {
        throwExceptionIfClosed()
        val downloadsWithTags = requestDatabase.requestDao()
            .getDownloadsByRequestIdentifier(identifier)
        val downloads = downloadsWithTags.map {
            it.download.apply { tags = it.tags.map { tag -> tag.title } }
        }
        sanitize(downloads)
        return downloads
    }

    override fun getDownloadsByIdentifier(identifier: List<Long>): List<DownloadInfo?> {
        throwExceptionIfClosed()
        val downloadsWithTags = requestDatabase.requestDao()
            .getDownloadsByIdentifier(identifier)
        val downloads = downloadsWithTags.map {
            it.download.apply { tags = it.tags.map { tag -> tag.title } }
        }
        sanitize(downloads)
        return downloads
    }

    override fun getPendingDownloadsSorted(prioritySort: PrioritySort): List<DownloadInfo> {
        throwExceptionIfClosed()
        val downloads = if (prioritySort == PrioritySort.ASC) {
            requestDatabase.requestDao().getPendingDownloadsSorted(Status.QUEUED)
        } else {
            requestDatabase.requestDao().getPendingDownloadsSortedDesc(Status.QUEUED)
        }
        var downloadsWrappers: List<DownloadInfo> = downloads.map {
            it.download.apply {
                tags = it.tags.map { t -> t.title }
            }
            return@map it.download
        }
        if (sanitize(downloadsWrappers)) {
            downloadsWrappers = downloadsWrappers.filter { it.status == Status.QUEUED }
        }
        return downloadsWrappers
    }

    override fun getAllGroupIds(): List<Int> {
        throwExceptionIfClosed()
        return requestDatabase.requestDao().getAllGroupIds()
    }

    override fun getDownloadsByTag(tag: String): List<DownloadInfo> {
        throwExceptionIfClosed()
        val tagId = Tag.generateId(tag)
        val downloadsAndTag = requestDatabase.tagDao().getDownloadsByTag(tagId)
        val downloads = downloadsAndTag?.downloads ?: emptyList()
        sanitize(downloads)
        return downloads
    }

    override fun getDownloadsByTags(tags: List<String>): List<DownloadInfo> {
        throwExceptionIfClosed()
        val tagIds = tags.map { Tag.generateId(it) }
        val tagsAndDownloads = requestDatabase.tagDao().getDownloadsByTags(tagIds)
        val downloads = tagsAndDownloads.map { it.downloads }.flatten()
        sanitize(downloads)
        return downloads
    }

    private fun executeInsertOrUpdateTag(info: DownloadInfo) {
        val tags: List<String> = when {
            info.tags.isNotEmpty() -> info.tags
            info.tag != null -> listOf(info.tag!!)
            else -> listOf()
        }

        tags.map {
            val tag = Tag(Tag.generateId(it), it)
            val tagRef = TagRef(tag.id, info.id)
            requestDatabase.tagDao().addOrUpdateTag(tag)
            requestDatabase.tagRefDao().addOrUpdateTagRef(tagRef)
        }
    }

    private val pendingCountQuery =
        "SELECT ${DownloadDatabase.COLUMN_ID} FROM ${DownloadDatabase.TABLE_NAME}" +
                " WHERE ${DownloadDatabase.COLUMN_STATUS} = '${Status.QUEUED.value}'" +
                " OR ${DownloadDatabase.COLUMN_STATUS} = '${Status.DOWNLOADING.value}'"

    private val pendingCountIncludeAddedQuery =
        "SELECT ${DownloadDatabase.COLUMN_ID} FROM ${DownloadDatabase.TABLE_NAME}" +
                " WHERE ${DownloadDatabase.COLUMN_STATUS} = '${Status.QUEUED.value}'" +
                " OR ${DownloadDatabase.COLUMN_STATUS} = '${Status.DOWNLOADING.value}'" +
                " OR ${DownloadDatabase.COLUMN_STATUS} = '${Status.ADDED.value}'"

    override fun getPendingCount(includeAddedDownloads: Boolean): Long {
        return try {
            val query =
                if (includeAddedDownloads) pendingCountIncludeAddedQuery else pendingCountQuery
            val cursor: Cursor? = database.query(query)
            val count = cursor?.count?.toLong() ?: -1L
            cursor?.close()
            count
        } catch (e: Exception) {
            -1
        }
    }

    override fun sanitizeOnFirstEntry() {
        throwExceptionIfClosed()
        liveSettings.execute {
            if (!it.didSanitizeDatabaseOnFirstEntry) {
                sanitize(get(), true)
                it.didSanitizeDatabaseOnFirstEntry = true
            }
        }
    }

    private val updatedDownloadsList = mutableListOf<DownloadInfo>()

    private fun sanitize(downloads: List<DownloadInfo>, firstEntry: Boolean = false): Boolean {
        updatedDownloadsList.clear()
        var downloadInfo: DownloadInfo
        for (element in downloads) {
            downloadInfo = element
            when (downloadInfo.status) {
                Status.COMPLETED -> onCompleted(downloadInfo)
                Status.DOWNLOADING -> onDownloading(downloadInfo, firstEntry)
                Status.QUEUED,
                Status.PAUSED -> onPaused(downloadInfo)
                Status.CANCELLED,
                Status.FAILED,
                Status.ADDED,
                Status.NONE,
                Status.DELETED,
                Status.REMOVED -> {
                }
            }
        }
        val updatedCount = updatedDownloadsList.size
        if (updatedCount > 0) {
            try {
                update(updatedDownloadsList)
            } catch (e: Exception) {
                logger.e("Failed to update", e)
            }
        }
        updatedDownloadsList.clear()
        return updatedCount > 0
    }

    private fun onPaused(downloadInfo: DownloadInfo) {
        if (downloadInfo.downloaded > 0) {
            if (fileExistChecksEnabled) {
                if (!defaultStorageResolver.fileExists(downloadInfo.file)) {
                    downloadInfo.downloaded = 0
                    downloadInfo.total = -1L
                    downloadInfo.error = defaultNoError
                    updatedDownloadsList.add(downloadInfo)
                    delegate?.deleteTempFilesForDownload(downloadInfo)
                }
            }
        }
    }

    private fun onDownloading(downloadInfo: DownloadInfo, firstEntry: Boolean) {
        if (firstEntry) {
            val status =
                if (downloadInfo.downloaded > 0 && downloadInfo.total > 0 && downloadInfo.downloaded >= downloadInfo.total) {
                    Status.COMPLETED
                } else {
                    Status.QUEUED
                }
            downloadInfo.status = status
            downloadInfo.error = defaultNoError
            updatedDownloadsList.add(downloadInfo)
        }
    }

    private fun onCompleted(downloadInfo: DownloadInfo) {
        if (downloadInfo.total < 1 && downloadInfo.downloaded > 0) {
            downloadInfo.total = downloadInfo.downloaded
            downloadInfo.error = defaultNoError
            updatedDownloadsList.add(downloadInfo)
        }
    }

    private fun sanitize(downloadInfo: DownloadInfo?, initializing: Boolean = false): Boolean {
        return if (downloadInfo == null) {
            false
        } else {
            sanitize(listOf(downloadInfo), initializing)
        }
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        try {
            database.close()
        } catch (e: Exception) {

        }
        try {
            requestDatabase.close()
        } catch (e: Exception) {

        }
        logger.d("Database closed")
    }

    override fun getNewDownloadInfoInstance(): DownloadInfo {
        return DownloadInfo()
    }

    private fun throwExceptionIfClosed() {
        if (closed) {
            throw FetchException("$namespace database is closed")
        }
    }

}