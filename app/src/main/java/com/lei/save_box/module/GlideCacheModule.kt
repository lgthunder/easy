package com.lei.save_box.module

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.module.AppGlideModule

@GlideModule
class GlideCacheModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val cacheSizeBytes = 250L * 1024 * 1024
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, cacheSizeBytes))
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}