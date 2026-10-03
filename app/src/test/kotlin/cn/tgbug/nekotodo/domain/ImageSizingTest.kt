package cn.tgbug.nekotodo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSizingTest {

    @Test
    fun `大图按 2 的幂降采样到长边以内`() {
        // 4000x3000 降到 1920 以内:/2=2000 仍然超;/4=1000 合格
        assertEquals(4, ImageSizing.inSampleSize(4000, 3000))
    }

    @Test
    fun `刚好等于上限时不降采样`() {
        assertEquals(1, ImageSizing.inSampleSize(1920, 1080))
        assertEquals(1, ImageSizing.inSampleSize(1080, 1920))
    }

    @Test
    fun `竖图按长边计算`() {
        // 3000x4000 同样是 /4
        assertEquals(4, ImageSizing.inSampleSize(3000, 4000))
    }

    @Test
    fun `超大图会继续翻倍`() {
        // 8000x6000:/2=4000 超;/4=2000 超;/8=1000 合格
        assertEquals(8, ImageSizing.inSampleSize(8000, 6000))
    }

    @Test
    fun `尺寸未知时不做降采样`() {
        assertEquals(1, ImageSizing.inSampleSize(0, 0))
        assertEquals(1, ImageSizing.inSampleSize(-1, 100))
    }

    @Test
    fun `自定上限`() {
        assertEquals(2, ImageSizing.inSampleSize(1200, 900, maxDimension = 800))
    }
}
