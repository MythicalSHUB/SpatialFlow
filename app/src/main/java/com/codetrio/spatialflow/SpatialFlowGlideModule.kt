package com.codetrio.spatialflow

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.module.AppGlideModule

@GlideModule
class SpatialFlowGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val prefs = context.getSharedPreferences("spatialflow_settings", Context.MODE_PRIVATE)
        val maxSizeMb = prefs.getInt("image_cache_max_size", 128)
        val maxSizeBytes = (if (maxSizeMb <= 0) 128L else maxSizeMb.toLong()) * 1024L * 1024L
        
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, "image_cache", maxSizeBytes))
    }
}
