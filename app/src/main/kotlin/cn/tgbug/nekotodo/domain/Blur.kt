package cn.tgbug.nekotodo.domain

/**
 * 纯 Kotlin 的箱式模糊(三次 ≈ 高斯),直接作用在 ARGB 像素数组上。
 *
 * 为什么不用平台能力:`RenderEffect` 要 API 31+,`RenderScript`/`ScriptIntrinsicBlur`
 * 已废弃。自己实现的好处是**各版本观感一致**。
 *
 * 注意与"缩小再放大"的区别:后者只是丢分辨率,看起来是糊而不是虚;这里是真的把
 * 邻域像素做了加权平均。
 */
object Blur {

    /**
     * 原地模糊。[radius] 是像素半径,0 或负数时直接返回。
     * 水平与垂直各做可分离的一维模糊,所以复杂度与半径无关(滑动窗口)。
     */
    fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
        if (radius <= 0 || passes <= 0) return
        if (width <= 0 || height <= 0 || pixels.size < width * height) return
        repeat(passes) {
            blurRows(pixels, width, height, radius)
            blurColumns(pixels, width, height, radius)
        }
    }

    private fun channel(pixel: Int, shift: Int) = (pixel shr shift) and 0xFF

    private fun blurRows(pixels: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        val row = IntArray(width)
        for (y in 0 until height) {
            val base = y * width
            for (x in 0 until width) row[x] = pixels[base + x]

            var a = 0
            var r = 0
            var g = 0
            var b = 0
            // 边界按"夹取到最近像素"处理,避免出现黑边。
            for (i in -radius..radius) {
                val px = row[i.coerceIn(0, width - 1)]
                a += channel(px, 24); r += channel(px, 16); g += channel(px, 8); b += channel(px, 0)
            }
            for (x in 0 until width) {
                pixels[base + x] = ((a / window) shl 24) or
                    ((r / window) shl 16) or
                    ((g / window) shl 8) or
                    (b / window)
                val leaving = row[(x - radius).coerceIn(0, width - 1)]
                val entering = row[(x + radius + 1).coerceIn(0, width - 1)]
                a += channel(entering, 24) - channel(leaving, 24)
                r += channel(entering, 16) - channel(leaving, 16)
                g += channel(entering, 8) - channel(leaving, 8)
                b += channel(entering, 0) - channel(leaving, 0)
            }
        }
    }

    private fun blurColumns(pixels: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        val column = IntArray(height)
        for (x in 0 until width) {
            for (y in 0 until height) column[y] = pixels[y * width + x]

            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val px = column[i.coerceIn(0, height - 1)]
                a += channel(px, 24); r += channel(px, 16); g += channel(px, 8); b += channel(px, 0)
            }
            for (y in 0 until height) {
                pixels[y * width + x] = ((a / window) shl 24) or
                    ((r / window) shl 16) or
                    ((g / window) shl 8) or
                    (b / window)
                val leaving = column[(y - radius).coerceIn(0, height - 1)]
                val entering = column[(y + radius + 1).coerceIn(0, height - 1)]
                a += channel(entering, 24) - channel(leaving, 24)
                r += channel(entering, 16) - channel(leaving, 16)
                g += channel(entering, 8) - channel(leaving, 8)
                b += channel(entering, 0) - channel(leaving, 0)
            }
        }
    }
}
