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
        return ModelLoader.LoadData(
            com.bumptech.glide.signature.ObjectKey(model.filePath),
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
        Log.d("leiting", "VideoThumbnailDataFetcher: 开始加载 ${model.filePath}")
        val file = File(model.filePath)
        if (!file.exists()) {
            Log.d("leiting", "VideoThumbnailDataFetcher: 文件不存在 ${model.filePath}")
            callback.onLoadFailed(Exception("File not found"))
            return
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(model.filePath)
            Log.d("leiting", "VideoThumbnailDataFetcher: 提取帧 ${model.filePath}")

            val embedded = retriever.embeddedPicture
            val rawBitmap = if (embedded != null) {
                Log.d("leiting", "VideoThumbnailDataFetcher: 使用内嵌封面")
                BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
            } else {
                Log.d("leiting", "VideoThumbnailDataFetcher: 提取非黑屏帧")
                extractNonBlackFrame(retriever,model.time)
            }

            if (rawBitmap != null) {
                val scaled = Bitmap.createScaledBitmap(rawBitmap, width.coerceAtLeast(96), height.coerceAtLeast(96), true)
                if (scaled !== rawBitmap) rawBitmap.recycle()
                Log.d("leiting", "VideoThumbnailDataFetcher: 成功 ${model.filePath}")
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

    private fun extractNonBlackFrame(retriever: MediaMetadataRetriever,time:Long): Bitmap? {
        val offsets = longArrayOf(time*1000,1_000_000, 500_000, 2_000_000, 10_000_000, 0)
        for (offset in offsets) {
            val frame = retriever.getFrameAtTime(offset, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame != null && !isMostlyBlack(frame)) return frame
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

    override fun cleanup() {}
    override fun cancel() {}
    override fun getDataClass(): Class<Bitmap> = Bitmap::class.java
    override fun getDataSource(): DataSource = DataSource.LOCAL
}