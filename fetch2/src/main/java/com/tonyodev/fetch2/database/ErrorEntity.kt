package com.tonyodev.fetch2.database

import com.google.gson.annotations.SerializedName
import com.tonyodev.fetch2core.Downloader

data class ErrorEntity(
    @SerializedName("key") var key: Int,
    @SerializedName("message") var response: Downloader.Response? = null
)
