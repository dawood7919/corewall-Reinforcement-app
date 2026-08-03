package com.corewall.qaqc.ai.remote

import com.corewall.qaqc.ai.AiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * عميل HTTP بسيط ومقصود: نقطة نهاية واحدة (POST JSON) —
 * مفيش داعي لمكتبة شبكة كاملة. بيشتغل على Dispatchers.IO
 * بـ timeouts صريحة وتحويل أخطاء مفهومة للمستخدم.
 */
object AiHttpClient {

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 90_000   // التحليل ممكن ياخد وقت

    suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>
    ): String = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }

            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()

            when {
                code in 200..299 -> text
                code == 401 || code == 403 -> throw AiError.Unauthorized(text.take(300))
                code == 429 -> throw AiError.RateLimited(text.take(300))
                else -> throw AiError.Server(code, text.take(300))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiError) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AiError.Timeout
        } catch (e: UnknownHostException) {
            throw AiError.Network
        } catch (e: Exception) {
            throw AiError.Network
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
