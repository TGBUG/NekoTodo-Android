package cn.tgbug.nekotodo.domain

/**
 * 照片上传前要先降采样:后端单文件上限 20MB,而现在的手机主摄直出常常超过这个数。
 * 采样倍数取 2 的幂(BitmapFactory 只认 2 的幂),把它算成纯函数以便单测。
 */
object ImageSizing {

    /** 长边最多保留的像素数。 */
    const val MAX_DIMENSION = 1920

    /**
     * 返回能让宽高都不超过 [maxDimension] 的最小 2 的幂采样倍数。
     * 尺寸未知(<=0)时返回 1,即不降采样。
     */
    fun inSampleSize(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        var sample = 1
        while (width / sample > maxDimension || height / sample > maxDimension) {
            sample *= 2
        }
        return sample
    }
}
