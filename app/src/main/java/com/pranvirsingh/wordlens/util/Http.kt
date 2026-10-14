package com.pranvirsingh.wordlens.util

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

/**
 * GETs [url] and returns the body text, or null for "404 not found". Any
 * other failure throws [IOException]. Doesn't block a thread, and cancelling
 * the coroutine cancels the request.
 */
internal suspend fun OkHttpClient.getText(url: HttpUrl, userAgent: String, accept: String): String? {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", userAgent)
        .header("Accept", accept)
        .build()
    return newCall(request).await().use { response ->
        when {
            response.code == 404 -> null
            !response.isSuccessful -> throw IOException("HTTP ${response.code} from ${url.host}")
            else -> response.body?.string() ?: throw IOException("Empty response from ${url.host}")
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                // If the caller gave up meanwhile, close the response instead of leaking it.
                continuation.resume(response) { _, _, _ -> response.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        },
    )
}
