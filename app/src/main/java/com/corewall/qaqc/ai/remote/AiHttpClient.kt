package com.corewall.qaqc.ai.remote

import com.corewall.qaqc.ai.AiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 *
 * الشبكة في الموقع بتقطع وترجع، فالطلب بيتعاد لوحده على الأخطاء
 * المؤقتة بس (انقطاع، مهلة، ضغط، 5xx). المفتاح الغلط مابيتعادش.
 */
object AiHttpClient {

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 90_000   // التحليل ممكن ياخد وقت
    private const val CHUNK_BYTES = 32 * 1024

    private const val MAX_ATTEMPTS = 3
    private const val BACKOFF_MS = 1_500L

    suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>
    ): String {
        var last: AiError? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return attempt(url, body, headers)
            } catch (e: AiError) {
                if (!e.retryable || attempt == MAX_ATTEMPTS - 1) throw e
                last = e
                // تراجع أسّي: 1.5 ثانية، بعدين 3 — من غير ما نضغط على الخدمة
                delay(BACKOFF_MS * (attempt + 1))
            }
        }
        throw last ?: AiError.Network("فشل الطلب من غير سبب واضح")
    }

    /**
     * تحميل بايتس (GET).
     *
     * موجودة عشان بعض خدمات توليد الصور بترجّع **رابط** للصورة بدل
     * البايتس نفسها، والرابط ده مؤقت وبينتهي — فلازم ننزّله دلوقتي
     * ونحفظه، مش نسيبه للعرض بعدين.
     */
    suspend fun getBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val text = conn.errorStream?.bufferedReader(Charsets.UTF_8)
                    ?.use(BufferedReader::readText).orEmpty()
                throw AiError.Server(code, text.take(300))
            }
            conn.inputStream.use { it.readBytes() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiError) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AiError.Timeout
        } catch (e: UnknownHostException) {
            throw AiError.Offline
        } catch (e: OutOfMemoryError) {
            throw AiError.TooLarge("الصورة أكبر من الذاكرة المتاحة")
        } catch (e: Throwable) {
            throw AiError.Network("${e::class.java.simpleName}: ${e.message.orEmpty()}")
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private suspend fun attempt(
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

            // بنكتب النص مباشرة على الستريم (chunked) بدل ما نعمل نسخة byte[]
            // كاملة في الذاكرة — الطلبات اللي فيها صور بتبقى ميجابايتات.
            conn.setChunkedStreamingMode(CHUNK_BYTES)
            conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }

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
            // الـDNS مارجعش عنوان — دي علامة إن الجهاز أوفلاين، مش إن الخدمة واقعة
            throw AiError.Offline
        } catch (e: OutOfMemoryError) {
            // بيحصل مع طلبات الصور الكبيرة — Error مش Exception، فلازم نمسكه هنا
            throw AiError.TooLarge("الذاكرة مش كافية لإرسال الطلب")
        } catch (e: Throwable) {
            // أي حاجة تانية بتوصل للمستخدم بنوعها ورسالتها — مش رسالة عامة
            throw AiError.Network("${e::class.java.simpleName}: ${e.message.orEmpty()}")
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
