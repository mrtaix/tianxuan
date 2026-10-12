package top.wkbin.tianxuan.harness.knowledge

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 调用 OpenAI 兼容的 /embeddings 端点，把文本转成向量。
 *
 * 复用 chat 通路的 [OkHttpClient] 与 baseUrl/apiKey（来自 [top.wkbin.tianxuan.core.tools.ProviderRepository]），
 * 不引入额外依赖。
 */
class EmbeddingClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    @Serializable
    private data class EmbeddingResponse(val data: List<EmbeddingItem>)

    @Serializable
    private data class EmbeddingItem(val embedding: List<Float>)

    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    suspend fun embed(
        texts: List<String>,
        baseUrl: String,
        apiKey: String?,
        model: String = DEFAULT_MODEL,
        endpointSuffix: String = "/embeddings",
    ): List<FloatArray> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        val requestBody = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("input", buildJsonArray { texts.forEach { add(JsonPrimitive(it)) } })
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val endpoint = baseUrl.trimEnd('/') + endpointSuffix.trimStart('/').let {
            if (it.endsWith(endpointSuffix.trimStart('/'))) it else "${baseUrl.trimEnd('/')}${endpointSuffix}"
        }
        val request = Request.Builder()
            .url(endpoint)
            .apply { apiKey?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .post(requestBody)
            .build()

        val call = okHttpClient.newCall(request)
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion(onCancelling = true) { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body.string()
                    val maskedEndpoint = endpoint.substringBefore('/').take(40)
                    if (response.code == 404) {
                        throw IOException(
                            "Điểm cuối nhúng 404 NOT_FOUND (điểm cuối: $maskedEndpoint, model: $model):" +
                                "Base URL hiện tại có thể không cung cấp $endpointSuffix endpoint, hoặc tên model nhúng \"$model\" không khả dụng." +
                                "Hãy đặt「tên model nhúng」và「hậu tố endpoint nhúng」trong trang kho tri thức để khớp nhà cung cấp của bạn. Phản hồi: ${body.take(300)}"
                        )
                    }
                    if (response.code == 429) {
                        throw IOException("Giao diện nhúng bị giới hạn tần suất (429): hãy thử lại sau hoặc giảm tần suất thêm (endpoint: $maskedEndpoint)")
                    }
                    throw IOException("Lỗi giao diện nhúng HTTP ${response.code} (điểm cuối: $maskedEndpoint, model: $model): ${body.take(500)}")
                }
                val body = response.body.string()
                val parsed = runCatching { json.decodeFromString(EmbeddingResponse.serializer(), body) }
                    .getOrElse { parseEmbeddingsManually(body) }
                parsed.data.map { item -> FloatArray(item.embedding.size) { idx -> item.embedding[idx] } }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: java.net.SocketException) {
            val maskedEndpoint = endpoint.substringBefore('/').take(40)
            throw IOException(
                "Kết nối nhúng bị đóng (Socket closed): vui lòng kiểm tra Base URL của model đang kích hoạt có truy cập được không, mạng có bị giới hạn không, và API Key có hợp lệ không (điểm cuối: $maskedEndpoint)",
                e,
            )
        } catch (e: java.net.UnknownHostException) {
            throw IOException("Kết nối embedding thất bại: không phân giải được host (hãy kiểm tra chính tả Base URL và mạng thiết bị)", e)
        } finally {
            cancelHandle?.dispose()
        }
    }

    /** 备用解析：某些 provider 返回非标准结构时降级到直接取 data[].embedding。 */
    private fun parseEmbeddingsManually(body: String): EmbeddingResponse {
        val obj = json.parseToJsonElement(body).jsonObject
        val data = obj["data"]?.jsonArray ?: throw IOException("Embedding response missing 'data' field")
        val items = data.map { entry ->
            val embedding = entry.jsonObject["embedding"]?.jsonArray
                ?: throw IOException("Embedding item missing 'embedding' field")
            EmbeddingItem(embedding.map { it.jsonPrimitive.content.toFloat() })
        }
        return EmbeddingResponse(items)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        const val DEFAULT_MODEL = "text-embedding-3-small"
        const val DEFAULT_ENDPOINT_SUFFIX = "/embeddings"
    }
}
