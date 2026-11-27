package top.wkbin.tianxuan.runtime.privilege

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Executes shell commands through a Shizuku `newProcess()` runner over private
 * stdin/stdout pipes. This is the OEM-safe alternative to bindUserService(),
 * which never delivers onServiceConnected on some ROMs.
 */
internal class ShizukuCompatClient(
    private val process: IRemoteProcess,
    onDied: () -> Unit,
) {
    private val inputStream: InputStream = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
    private val outputStream: OutputStream = ParcelFileDescriptor.AutoCloseOutputStream(process.outputStream)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<String>>()
    private val nextId = AtomicLong(1)
    private val hello = CompletableDeferred<CompatResponse>()
    @Volatile var uid: Int = -1
    @Volatile private var closed = false

    private val reader = Thread({
        try {
            inputStream.bufferedReader().use { reader ->
                while (!closed) {
                    val line = reader.readLine() ?: break
                    val response = ShizukuCompatProtocol.json.decodeFromString<CompatResponse>(line)
                    if (response.id == 0L) hello.complete(response)
                    else pending.remove(response.id)?.complete(response.error ?: response.result)
                }
            }
        } catch (t: Throwable) {
            hello.completeExceptionally(t)
            pending.values.forEach { it.completeExceptionally(t) }
        }
    }, "CTHarnessCompatReader").apply { isDaemon = true }

    init {
        reader.start()
        runCatching { process.asBinder().linkToDeath({ onDied() }, 0) }
    }

    val isAlive: Boolean get() = !closed && runCatching { process.asBinder().pingBinder() }.getOrDefault(false)

    suspend fun request(command: List<String>, timeoutMs: Int): String = withContext(Dispatchers.IO) {
        check(isAlive) { "Compatibility runner is not alive" }
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<String>()
        pending[id] = deferred
        try {
            val line = ShizukuCompatProtocol.json.encodeToString(
                CompatRequest.serializer(),
                CompatRequest(id = id, operation = "exec", command = command, timeoutMs = timeoutMs),
            ) + "\n"
            synchronized(outputStream) {
                outputStream.write(line.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            withTimeout(timeoutMs.toLong() + 5_000L) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    fun close() {
        closed = true
        runCatching { outputStream.close() }
        runCatching { inputStream.close() }
        runCatching { process.destroy() }
    }

    companion object {
        suspend fun start(sourceDir: String, onDied: () -> Unit): ShizukuCompatClient = withContext(Dispatchers.IO) {
            check(Shizuku.getVersion() in 11..13) { "Shizuku version does not support newProcess" }
            val expectedUid = Shizuku.getUid()
            check(expectedUid == 0 || expectedUid == 2000) { "Shizuku did not provide shell/root privileges" }
            val server = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            val process = requireNotNull(
                server.newProcess(ShizukuCompatProtocol.launchCommand(sourceDir), null, null)
            ) { "newProcess returned null" }
            val client = try { ShizukuCompatClient(process, onDied) } catch (t: Throwable) {
                runCatching { process.destroy() }
                throw t
            }
            try {
                val hello = withTimeout(10_000) { client.hello.await() }
                check(hello.protocol == 1 && hello.uid == expectedUid) { "Compat runner identity mismatch" }
                val probe = client.request(listOf("/system/bin/id", "-u"), 3_000)
                check(probe.startsWith("exit=0\ntimeout=0\n${expectedUid}\n")) {
                    "Compat runner execution check failed: $probe"
                }
                client.uid = hello.uid
                client
            } catch (t: Throwable) {
                client.close()
                throw t
            }
        }
    }
}
