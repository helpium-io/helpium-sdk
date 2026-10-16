package io.helpium.sdk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import okhttp3.Request
import java.util.concurrent.Executors

/** Loads remote images (avatars, attachments) with a small memory cache. */
internal object ImageLoader {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun load(url: String?, into: ImageView, maxSize: Int = 1024) {
        into.tag = url
        if (url == null) return
        cache.get(url)?.let { into.setImageBitmap(it); return }
        into.setImageDrawable(null)
        pool.execute {
            val bitmap = runCatching {
                (Helpium.store?.api?.http ?: Api.defaultClient).newCall(Request.Builder().url(url).build()).execute().use { response ->
                    response.body?.bytes()?.let { decode(it, maxSize) }
                }
            }.getOrNull() ?: return@execute
            cache.put(url, bitmap)
            main.post { if (into.tag == url) into.setImageBitmap(bitmap) }
        }
    }

    fun decode(bytes: ByteArray, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
