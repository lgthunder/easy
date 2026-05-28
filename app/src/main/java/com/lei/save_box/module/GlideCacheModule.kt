package com.lei.save_box.module

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.module.AppGlideModule
import com.lei.save_box.glide.VideoThumbnail
import com.lei.save_box.glide.VideoThumbnailLoader

@GlideModule
class GlideCacheModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val cacheSizeBytes = 250L * 1024 * 1024
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, cacheSizeBytes))
        
        // 启用 Glide 详细日志
        builder.setLogLevel(Log.VERBOSE)
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.append(
            VideoThumbnail::class.java,
            Bitmap::class.java,
            VideoThumbnailLoader.Factory()
        )
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}