package top.wkbin.tianxuan.iteration.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 守护「双包共存」身份契约。
 *
 * 这些断言的意图不是测 Regex 本身，而是把一组很容易在后续改动中被悄悄破坏的
 * 不变式钉死：预览包包名必须是合法 reverse-DNS、必须与正式版不同、应用名非空。
 * 一旦有人把包名改回与正式版相同、或填了非法段，这里会立刻变红，
 * 从而在 CI 阶段拦住「装完互相覆盖 / 抢权限」的回退。
 */
class CustomIterationIdentityTest {

    @Test
    fun `dev identity constants are the expected values`() {
        assertEquals("com.mvibe.mobi", CustomIterationBootstrap.DEV_APPLICATION_ID)
        assertEquals("Mvibe", CustomIterationBootstrap.DEV_APP_LABEL)
    }

    @Test
    fun `default dev identity passes validation`() {
        assertNull(CustomIterationBootstrap.validateDevIdentity())
    }

    @Test
    fun `dev application id differs from official to allow coexistence`() {
        assertNotEquals(
            CustomIterationBootstrap.OFFICIAL_APPLICATION_ID,
            CustomIterationBootstrap.DEV_APPLICATION_ID
        )
    }

    @Test
    fun `identity equal to official package is rejected`() {
        val reason = CustomIterationBootstrap.validateDevIdentity(
            applicationId = CustomIterationBootstrap.OFFICIAL_APPLICATION_ID
        )
        assertNotNull("与正式版同包名必须被拒绝", reason)
    }

    @Test
    fun `blank app label is rejected`() {
        assertNotNull(CustomIterationBootstrap.validateDevIdentity(appLabel = ""))
        assertNotNull(CustomIterationBootstrap.validateDevIdentity(appLabel = "   "))
    }

    @Test
    fun `single segment application id is rejected`() {
        assertNotNull(CustomIterationBootstrap.validateDevIdentity(applicationId = "mvibe"))
    }

    @Test
    fun `segment starting with digit is rejected`() {
        assertNotNull(CustomIterationBootstrap.validateDevIdentity(applicationId = "com.1mvibe.mobi"))
    }

    @Test
    fun `segment with uppercase or illegal char is rejected`() {
        assertNotNull(CustomIterationBootstrap.validateDevIdentity(applicationId = "com.Mvibe.mobi"))
        assertNotNull(CustomIterationBootstrap.validateDevIdentity(applicationId = "com.mvibe-x.mobi"))
    }

    @Test
    fun `valid custom reverse dns passes`() {
        assertNull(CustomIterationBootstrap.validateDevIdentity(applicationId = "com.mvibe.mobi"))
        assertNull(CustomIterationBootstrap.validateDevIdentity(applicationId = "top.wkbin.tianxuan.dev"))
        assertNull(
            CustomIterationBootstrap.validateDevIdentity(
                applicationId = "io.example.app_one",
                appLabel = "Example"
            )
        )
    }
}
