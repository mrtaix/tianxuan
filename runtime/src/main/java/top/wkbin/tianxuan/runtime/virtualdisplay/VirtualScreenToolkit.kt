package top.wkbin.tianxuan.runtime.virtualdisplay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.wkbin.tianxuan.runtime.gui.GuiBackendId
import top.wkbin.tianxuan.runtime.gui.GuiExecResult
import top.wkbin.tianxuan.runtime.gui.GuiKey
import top.wkbin.tianxuan.runtime.gui.GuiPrimitive
import top.wkbin.tianxuan.runtime.gui.toSwipe

/**
 * 虚拟屏 GUI 原语执行器：复用主屏的 [GuiPrimitive] 体系，把动作定向到
 * [VirtualDisplayCoordinator] 管理的 Shower 虚拟屏。
 *
 * 与主屏 [top.wkbin.tianxuan.runtime.gui.HostGuiToolkit] 的差异：
 * - 虚拟屏输入走 shower-server 的 Binder 注入（InputManager.injectInputEvent +
 *   setDisplayId），无多后端降级链——server 端自带拟人化抖动，成功率即可用性；
 * - PasteText 通过主屏剪贴板（系统全局共享）+ KEYCODE_PASTE 注入，依赖目标
 *   应用响应 PASTE 键；虚拟屏无无障碍节点树，不支持 ACTION_SET_TEXT 直设。
 */
class VirtualScreenToolkit(
    private val context: Context,
    private val coordinator: VirtualDisplayCoordinator,
) {

    suspend fun execute(sessionId: String, action: GuiPrimitive): GuiExecResult =
        withContext(Dispatchers.IO) {
            val backend = GuiBackendId.SHOWER_VIRTUAL_DISPLAY
            val controller = coordinator.controller(sessionId)
            if (coordinator.getDisplayId(sessionId) == null) {
                return@withContext GuiExecResult(
                    false,
                    "Màn hình ảo chưa được tạo (session=$sessionId): hãy gọi virtual_screen_ensure trước",
                    backend,
                )
            }
            when (action) {
                is GuiPrimitive.Tap -> {
                    if (controller.tap(action.x, action.y)) {
                        GuiExecResult(true, "Đã bấm trên màn hình ảo (${action.x},${action.y})", backend)
                    } else {
                        GuiExecResult(false, "Bấm màn hình ảo thất bại (${action.x},${action.y})", backend)
                    }
                }

                is GuiPrimitive.DoubleTap -> {
                    val first = controller.tap(action.x, action.y)
                    delay(action.gapMs.coerceIn(40L, 400L))
                    val second = controller.tap(action.x, action.y)
                    if (first && second) {
                        GuiExecResult(true, "Đã nhấp đúp trên màn hình ảo (${action.x},${action.y})", backend)
                    } else {
                        GuiExecResult(false, "Nhấp đúp màn hình ảo thất bại (${action.x},${action.y})", backend)
                    }
                }

                is GuiPrimitive.LongPress -> {
                    val hold = action.durationMs.coerceIn(200L, 5_000L)
                    // 用同点 swipe 在 server 端线程内原子完成长按：
                    // 若走 touchDown→delay→touchUp，协程在 delay 间被取消会让 touchUp
                    // 不再执行，虚拟屏上残留一个永不抬起的触点。
                    if (controller.swipe(action.x, action.y, action.x, action.y, hold)) {
                        GuiExecResult(true, "Đã nhấn giữ trên màn hình ảo (${action.x},${action.y}) ${hold}ms", backend)
                    } else {
                        GuiExecResult(false, "Nhấn giữ màn hình ảo thất bại (${action.x},${action.y})", backend)
                    }
                }

                is GuiPrimitive.Swipe -> {
                    val dur = action.durationMs.coerceIn(50L, 5_000L)
                    if (controller.swipe(action.x1, action.y1, action.x2, action.y2, dur)) {
                        GuiExecResult(
                            true,
                            "Đã vuốt trên màn hình ảo (${action.x1},${action.y1})→(${action.x2},${action.y2})",
                            backend,
                        )
                    } else {
                        GuiExecResult(false, "Vuốt màn hình ảo thất bại", backend)
                    }
                }

                is GuiPrimitive.Scroll -> {
                    val size = coordinator.getVideoSize(sessionId)
                    if (size == null) {
                        return@withContext GuiExecResult(false, "Kích thước màn hình ảo chưa biết (luồng video chưa sẵn sàng)", backend)
                    }
                    val (width, height) = size
                    val mapped = action.direction.toSwipe(
                        width = width,
                        height = height,
                        distanceRatio = action.distanceRatio,
                        durationMs = action.durationMs,
                        anchorX = action.anchorX,
                        anchorY = action.anchorY,
                    )
                    if (controller.swipe(mapped.x1, mapped.y1, mapped.x2, mapped.y2, mapped.durationMs)) {
                        GuiExecResult(
                            true,
                            "Đã cuộn trên màn hình ảo ${action.direction.name.lowercase()}",
                            backend,
                        )
                    } else {
                        GuiExecResult(false, "Cuộn màn hình ảo thất bại", backend)
                    }
                }

                is GuiPrimitive.Key -> {
                    if (controller.key(action.key.keyCode)) {
                        GuiExecResult(
                            true,
                            "Đã kích hoạt phím trên màn hình ảo: ${action.key.name.lowercase()} (${action.key.keyCode})",
                            backend,
                        )
                    } else {
                        GuiExecResult(false, "Nhấn phím màn hình ảo thất bại: ${action.key.name.lowercase()}", backend)
                    }
                }

                is GuiPrimitive.PasteText -> pasteText(sessionId, action.text, backend)
            }
        }

    /**
     * 虚拟屏文本输入：写入系统剪贴板（虚拟屏与主屏共享同一 ClipboardService）后
     * 注入 KEYCODE_PASTE。目标应用不响应 PASTE 键时失败——此时建议 AI 改用
     * 逐字符 key 注入或该应用自带的备选输入路径。
     */
    private suspend fun pasteText(
        sessionId: String,
        text: String,
        backend: GuiBackendId,
    ): GuiExecResult {
        if (text.isEmpty()) {
            return GuiExecResult(false, "Văn bản dán rỗng", backend)
        }
        if (!writeClipboard(text)) {
            return GuiExecResult(false, "Ghi vào clipboard thất bại, dán màn hình ảo bị hủy", backend)
        }
        delay(PASTE_SETTLE_MS)
        val controller = coordinator.controller(sessionId)
        return if (controller.key(GuiKey.PASTE.keyCode)) {
            GuiExecResult(true, "Đã dán qua clipboard trên màn hình ảo (${text.length} ký tự): $text", backend)
        } else {
            GuiExecResult(false, "Chèn KEYCODE_PASTE thất bại: ứng dụng mục tiêu có thể không phản hồi phím dán", backend)
        }
    }

    private fun writeClipboard(text: String): Boolean {
        val latch = CountDownLatch(1)
        var error: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("tianxuan-vscreen", text))
            } catch (t: Throwable) {
                error = t
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(3, TimeUnit.SECONDS)) return false
        return error == null
    }

    private companion object {
        const val PASTE_SETTLE_MS = 180L
    }
}
