package com.ghaith.ironhud.ai

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.BufferedSource
import java.util.concurrent.TimeUnit

internal val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

object Http {
    /** One shared client: HTTP/2 + a warm connection pool is a big part of "fast". */
    fun newClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .build()
}

/**
 * Executes the call on the IO dispatcher and hands the open response to [block].
 * Cancelling the calling coroutine cancels the HTTP call, even mid-stream.
 */
internal suspend fun <T> Call.executeAndUse(block: suspend (Response) -> T): T = coroutineScope {
    val call = this@executeAndUse
    val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            call.cancel()
        }
    }
    try {
        withContext(Dispatchers.IO) { call.execute().use { block(it) } }
    } finally {
        watcher.cancel()
    }
}

/** Minimal Server-Sent Events reader: yields the payload of each event's `data:` lines. */
internal fun BufferedSource.sseData(): Sequence<String> = sequence {
    val buf = StringBuilder()
    while (true) {
        val line = readUtf8Line() ?: break
        when {
            line.isEmpty() -> if (buf.isNotEmpty()) {
                yield(buf.toString())
                buf.setLength(0)
            }
            line.startsWith(":") -> Unit // comment / keep-alive
            line.startsWith("data:") -> {
                if (buf.isNotEmpty()) buf.append('\n')
                buf.append(line.removePrefix("data:").removePrefix(" "))
            }
            else -> Unit // event:, id:, retry: are irrelevant here
        }
    }
    if (buf.isNotEmpty()) yield(buf.toString())
}
