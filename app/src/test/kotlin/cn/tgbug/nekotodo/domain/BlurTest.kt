package cn.tgbug.nekotodo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurTest {

    private fun opaqueGray(value: Int) = (0xFF shl 24) or (value shl 16) or (value shl 8) or value

    private fun red(pixel: Int) = (pixel shr 16) and 0xFF

    private fun solid(width: Int, height: Int, value: Int) =
        IntArray(width * height) { opaqueGray(value) }

    @Test
    fun `单个亮点会被摊开到邻域`() {
        val size = 21
        val pixels = solid(size, size, 0)
        val center = 10 * size + 10
        pixels[center] = opaqueGray(255)

        Blur.boxBlur(pixels, size, size, radius = 3, passes = 1)

        // 箱式模糊产生的是**平坦平台**而不是尖峰:半径为 r 时,周围 (2r+1)^2 个像素同值。
        assertTrue("中心应被摊开", red(pixels[center]) < 255)
        assertEquals("平台内应同值", red(pixels[center]), red(pixels[10 * size + 12]))
        assertEquals("平台内应同值", red(pixels[center]), red(pixels[9 * size + 10]))
        // 平台上的一格应约等于 亮度/格数
        assertEquals(255 / (7 * 7), red(pixels[center]))
        // 半径之外必须完全不受影响
        assertEquals("半径外不该被影响", 0, red(pixels[10 * size + 6]))
        assertEquals("半径外不该被影响", 0, red(pixels[10 * size + 14]))
    }

    @Test
    fun `均匀图模糊后保持不变`() {
        val pixels = solid(9, 9, 128)
        Blur.boxBlur(pixels, 9, 9, radius = 4, passes = 3)

        // 边缘按夹取处理,所以整图应仍是同一个灰
        assertTrue("均匀图不该出现黑边或亮边", pixels.all { it == opaqueGray(128) })
    }

    @Test
    fun `模糊不会跨行或跨列串数据`() {
        val width = 9
        val height = 9
        val pixels = solid(width, height, 0)
        pixels[0] = opaqueGray(255) // 左上角一个亮点,半径 1、一遍之后右下角必须完全没变

        Blur.boxBlur(pixels, width, height, radius = 1, passes = 1)

        assertEquals("右下角不该被左上角影响", opaqueGray(0), pixels[width * height - 1])
        assertEquals("右上角不该被影响", opaqueGray(0), pixels[width - 1])
        assertEquals("左下角不该被影响", opaqueGray(0), pixels[(height - 1) * width])
    }

    @Test
    fun `多次模糊比一次更糊`() {
        val once = solid(21, 21, 0)
        val thrice = solid(21, 21, 0)
        val center = 10 * 21 + 10
        once[center] = opaqueGray(255)
        thrice[center] = opaqueGray(255)

        Blur.boxBlur(once, 21, 21, radius = 2, passes = 1)
        Blur.boxBlur(thrice, 21, 21, radius = 2, passes = 3)

        // 三次之后中心更暗(能量摊得更开)
        assertTrue("三次应更糊", red(thrice[center]) < red(once[center]))
        // 单遍的支撑是 ±2,三次能到 ±4;距离 3 处只有三遍才够得着。
        assertEquals("单遍不该够到距离 3", 0, red(once[center + 3]))
        assertTrue("三次应扩散更远", red(thrice[center + 3]) > 0)
    }

    @Test
    fun `半径为零或非法尺寸时原样返回`() {
        val pixels = solid(5, 5, 100)
        val before = pixels.copyOf()

        Blur.boxBlur(pixels, 5, 5, radius = 0)
        assertTrue("半径 0 不该改动", pixels.contentEquals(before))

        Blur.boxBlur(pixels, 0, 0, radius = 3)
        assertTrue("非法尺寸不该改动", pixels.contentEquals(before))

        Blur.boxBlur(pixels, 5, 5, radius = 3, passes = 0)
        assertTrue("0 遍不该改动", pixels.contentEquals(before))
    }

    @Test
    fun `竖直方向同样被模糊`() {
        val size = 21
        val pixels = solid(size, size, 0)
        val center = 10 * size + 10
        pixels[center] = opaqueGray(255)

        Blur.boxBlur(pixels, size, size, radius = 3, passes = 1)

        assertTrue("上邻域应被点亮", red(pixels[7 * size + 10]) > 0)
        assertTrue("下邻域应被点亮", red(pixels[13 * size + 10]) > 0)
    }
}
