package top.wkbin.tianxuan.runtime.privilege

import android.system.Os
import androidx.annotation.Keep
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Runs in the Shizuku-spawned process (shell/root uid) via `app_process`, not as
 * an Android Application. Avoiding `makeApplication()` is what keeps this path
 * working on OEM ROMs where bindUserService() hangs before onServiceConnected.
 */
@Keep
object ShizukuCompatMain {
    @Keep
    @JvmStatic
    fun main(args: Array<String>) {
        val json = ShizukuCompatProtocol.json
        val output = System.out.bufferedWriter()
        val running = ConcurrentHashMap<Long, Thread>()
        fun respond(response: CompatResponse) = synchronized(output) {
            output.write(json.encodeToString(CompatResponse.serializer(), response))
            output.newLine()
            output.flush()
        }
        respond(CompatResponse(0, uid = Os.getuid()))
        try {
            System.`in`.bufferedReader().use { input ->
                while (true) {
                    val line = input.readLine() ?: break
                    val request = json.decodeFromString<CompatRequest>(line)
                    if (request.operation == "cancel") {
                        running.remove(request.target)?.interrupt()
                        continue
                    }
                    if (request.operation == "close") break
                    val worker = Thread({
                        val result = when (request.operation) {
                            "exec" -> {
                                val pb = ProcessBuilder(request.command)
                                request.environment?.let { env -> pb.environment().clear(); env.forEach { e ->
                                    val i = e.indexOf('='); if (i > 0) pb.environment()[e.substring(0, i)] = e.substring(i + 1)
                                } }
                                val p = pb.start()
                                val done = p.waitFor(request.timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                                if (!done) { p.destroyForcibly(); "exit=-1\ntimeout=1\n" }
                                else "exit=${p.exitValue()}\ntimeout=0\n${p.inputStream.readBytes().toString(Charsets.UTF_8)}"
                            }
                            else -> "error=unknown operation"
                        }
                        respond(CompatResponse(request.id, result = result))
                    }, "CTHarnessCompatCommand-${request.id}").apply { isDaemon = true }
                    running[request.id] = worker
                    worker.start()
                }
            }
        } finally {
            running.values.forEach { it.interrupt() }
        }
    }
}
