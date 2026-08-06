package com.corewall.qaqc.ai.remote

import com.corewall.qaqc.ai.AiError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * بثّ الرد رمز برمز (SSE).
 *
 * ليه ده محتاج مكتبة: `HttpURLConnection` بيقرا الجسم كله قبل ما ترجّعه،
 * فالبثّ مستحيل عليه أصلاً. ومن غير بثّ، مؤشّر "بيفكّر" بيبقى كدب —
 * التطبيق مش عارف الموديل طلّع رمز واحد ولا تسعميت رمز، وبيلف على نفسه
 * لحد ما الرد كله يوصل.
 *
 * العميل القديم [AiHttpClient] فاضل زي ما هو للطلبات اللي مالهاش بثّ
 * (تحليل مستند، توليد تقرير) — مفيش داعي نلمسها.
 */
object AiStreamClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // القراية بتفضل مفتوحة طول البثّ، فالمهلة هنا لازم تكون سخية:
            // دي مهلة **الصمت** بين رمز ورمز، مش مهلة الرد كله.
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** حدث واحد من البثّ. */
    sealed interface Chunk {
        /** جزء نصّ جديد — بيتضاف على اللي قبله. */
        data class Delta(val text: String) : Chunk

        /** البثّ خلص بنجاح. */
        data object Done : Chunk
    }

    /**
     * بيفتح بثّاً ويرجّع تدفّق أجزاء.
     *
     * الإلغاء بيقفل الاتصال فعلاً — لو المستخدم خرج من الشاشة، الطلب
     * بيتلغي بدل ما يفضل شغّال وياكل من رصيده.
     */
    fun stream(
        url: String,
        body: String,
        headers: Map<String, String>
    ): Flow<Chunk> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .addHeader("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                // نهاية البثّ في البروتوكول المتوافق مع OpenAI.
                if (data.trim() == "[DONE]") {
                    trySend(Chunk.Done)
                    close()
                    return
                }
                val delta = parseDelta(data)
                if (!delta.isNullOrEmpty()) trySend(Chunk.Delta(delta))
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(Chunk.Done)
                close()
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                val code = response?.code ?: 0
                val detail = runCatching { response?.body?.string().orEmpty() }.getOrDefault("")
                close(
                    when {
                        code == 401 || code == 403 -> AiError.Server(code, "المفتاح مرفوض")
                        code >= 400 -> AiError.Server(code, detail)
                        t is IOException -> AiError.Offline
                        t != null -> AiError.Network(t.message.orEmpty())
                        else -> AiError.Network("البثّ اتقطع")
                    }
                )
            }
        }

        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    /**
     * بيطلّع جزء النصّ من حدث SSE.
     *
     * بيتسامح مع الاختلافات بين المزوّدين: بعضهم بيحطّ النصّ في
     * `choices[0].delta.content` وبعضهم في `choices[0].message.content`.
     * أي شكل تاني بيترجع null بدل ما يرمي — جزء واحد مش مفهوم مايستاهلش
     * يوقّع البثّ كله.
     */
    private fun parseDelta(data: String): String? = runCatching {
        val obj = json.parseToJsonElement(data).jsonObject
        val choice = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val fromDelta = choice?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.content
        if (!fromDelta.isNullOrEmpty()) fromDelta
        else choice?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
    }.getOrNull()
}
