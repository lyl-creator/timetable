package com.lyl.timetable.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本比较逻辑验证（更新检查的判定基础，纯逻辑、不联网）。
 */
class UpdateCheckerTest {

    @Test
    fun `新版本判定`() {
        assertTrue(UpdateChecker.compareVersions("1.2.0", "1.1.0") > 0)
        assertTrue(UpdateChecker.compareVersions("1.1.0", "1.2.0") < 0)
        assertEquals(0, UpdateChecker.compareVersions("1.1.0", "1.1.0"))
    }

    @Test
    fun `带 v 前缀的标签可正常比较`() {
        assertTrue(UpdateChecker.compareVersions("v1.2.0", "1.1.0") > 0)
        assertTrue(UpdateChecker.compareVersions("V2.0.0", "1.9.9") > 0)
        assertEquals(0, UpdateChecker.compareVersions("v1.1.0", "1.1.0"))
    }

    @Test
    fun `按数值而非字典序比较段位`() {
        assertTrue("1.10.0 应大于 1.9.0", UpdateChecker.compareVersions("1.10.0", "1.9.0") > 0)
        assertTrue("1.2.10 应大于 1.2.9", UpdateChecker.compareVersions("1.2.10", "1.2.9") > 0)
    }

    @Test
    fun `段数不等时缺失位按 0 处理`() {
        assertEquals(0, UpdateChecker.compareVersions("1.1", "1.1.0"))
        assertTrue(UpdateChecker.compareVersions("1.1.1", "1.1") > 0)
        assertTrue(UpdateChecker.compareVersions("2", "1.9.9") > 0)
    }

    @Test
    fun `预发布后缀不参与比较`() {
        assertEquals(0, UpdateChecker.compareVersions("1.2.0-beta.1", "1.2.0"))
        assertEquals(0, UpdateChecker.compareVersions("1.2.0+build.5", "1.2.0"))
    }

    @Test
    fun `异常输入不会崩溃`() {
        assertEquals(0, UpdateChecker.compareVersions("", ""))
        assertTrue(UpdateChecker.compareVersions("1.0.0", "abc") > 0)
    }

    @Test
    fun `仓库信息常量与发布页地址一致`() {
        assertEquals("lyl-creator/timetable", UpdateChecker.REPO)
        assertEquals("https://github.com/lyl-creator/timetable/releases", UpdateChecker.RELEASES_PAGE)
    }
}
