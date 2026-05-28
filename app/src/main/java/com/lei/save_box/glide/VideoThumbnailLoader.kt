package com.lei.save_box.glide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import java.io.File

class VideoThumbnailLoader : ModelLoader<VideoThumbnail, Bitmap> {

    override fun handles(model: VideoThumbnail): Boolean {
        return File(model.filePath).exists()
    }

    override fun buildLoadData(
        model: VideoThumbnail,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<Bitmap> {
        val cacheKey = "${model.filePath}_${model.time}"
        Log.d("leiting", "VideoThumbnailLoader: buildLoadData cacheKey=$cacheKey")
        return ModelLoader.LoadData(
            com.bumptech.glide.signature.ObjectKey(cacheKey),
            VideoThumbnailDataFetcher(model, width, height)
        )
    }

    class Factory : ModelLoaderFactory<VideoThumbnail, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<VideoThumbnail, Bitmap> {
            return VideoThumbnailLoader()
        }

        override fun teardown() {}
    }
}

class VideoThumbnailDataFetcher(
    private val model: VideoThumbnail,
    private val width: Int,
    private val height: Int
) : DataFetcher<Bitmap> {

    override fun loadData(priority: com.bumptech.glide.Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        Log.d("leiting", "VideoThumbnailDataFetcher: loadData 被调用 filePath=${model.filePath} time=${model.time}")
        val file = File(model.filePath)
        if (!file.exists()) {
            Log.d("leiting", "VideoThumbnailDataFetcher: 文件不存在 ${model.filePath}")
            callback.onLoadFailed(Exception("File not found"))
            return
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(model.filePath)
            Log.d("leiting", "VideoThumbnailDataFetcher: 开始提取帧 time=${model.time} ${model.filePath}")

            val embedded = retriever.embeddedPicture
            val rawBitmap = if (embedded != null) {
                Log.d("leiting", "VideoThumbnailDataFetcher: 使用内嵌封面")
                BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
            } else {
                Log.d("leiting", "VideoThumbnailDataFetcher: 提取非黑屏帧 time=${model.time}")
                extractNonBlackFrame(retriever, model.time)
            }

            if (rawBitmap != null) {
                val scaled = Bitmap.createScaledBitmap(rawBitmap, width.coerceAtLeast(96), height.coerceAtLeast(96), true)
                if (scaled !== rawBitmap) rawBitmap.recycle()
                Log.d("leiting", "VideoThumbnailDataFetcher: 提取成功，存入缓存 filePath=${model.filePath} time=${model.time}")
                callback.onDataReady(scaled)
            } else {
                Log.d("leiting", "VideoThumbnailDataFetcher: 提取失败 ${model.filePath}")
                callback.onLoadFailed(Exception("Failed to extract frame"))
            }
        } catch (e: Exception) {
            Log.e("leiting", "VideoThumbnailDataFetcher: 异常 ${model.filePath}", e)
            callback.onLoadFailed(e)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun extractNonBlackFrame(retriever: MediaMetadataRetriever, time: Long): Bitmap? {
        val offsets = mutableListOf<Long>()
        if (time > 0) offsets.add(time * 1000)
        offsets.addAll(listOf(1_000_000L, 500_000L, 2_000_000L, 10_000_000L, 0L))

        for (offset in offsets) {
            val frame = retriever.getFrameAtTime(offset, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame != null && !isMostlyBlack(frame) && !isMostlyWhite(frame)) return frame
        }
        return retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    private fun isMostlyBlack(bitmap: Bitmap): Boolean {
        val sampleSize = 10
        var darkPixels = 0
        var totalSamples = 0
        for (y in 0 until bitmap.height step sampleSize) {
            for (x in 0 until bitmap.width step sampleSize) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (r < 30 && g < 30 && b < 30) darkPixels++
                totalSamples++
            }
        }
        return totalSamples > 0 && darkPixels.toFloat() / totalSamples > 0.8f
    }

    private fun isMostlyWhite(bitmap: Bitmap): Boolean {
        val sampleSize = 10
        var lightPixels = 0
        var totalSamples = 0
        for (y in 0 until bitmap.height step sampleSize) {
            for (x in 0 until bitmap.width step sampleSize) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (r > 225 && g > 225 && b > 225) lightPixels++
                totalSamples++
            }
        }
        return totalSamples > 0 && lightPixels.toFloat() / totalSamples > 0.8f
    }

    override fun cleanup() {}
    override fun cancel() {}
    override fun getDataClass(): Class<Bitmap> = Bitmap::class.java
    override fun getDataSource(): DataSource = DataSource.LOCAL
}