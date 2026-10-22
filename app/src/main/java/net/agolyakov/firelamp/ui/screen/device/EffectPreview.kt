package net.agolyakov.firelamp.ui.screen.device

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import okio.Path.Companion.toOkioPath

/**
 * Preview GIFs of the effects (16×16 frames enlarged, named by effect code). The ones of the
 * firmware the APK was built with are bundled in `assets/previews/` (see syncEffectPreviews in
 * app/build.gradle.kts); an effect of a newer firmware is downloaded from the firmware repo and
 * kept in the disk cache. No preview at all leaves a black square.
 */
object EffectPreviews {
    private const val BASE_URL = "https://raw.githubusercontent.com/golyakoff/FireLamp_ESP32/main/preview/"
    private const val ASSETS_DIR = "previews"

    @Volatile
    private var bundled: Set<String>? = null

    @Volatile
    private var loaders: Pair<ImageLoader, ImageLoader>? = null

    /** The bundled GIF of [code], or its URL. */
    fun model(context: Context, code: String): String {
        val codes = bundled ?: context.assets.list(ASSETS_DIR).orEmpty()
            .map { it.removeSuffix(".gif") }.toSet()
            .also { bundled = it }
        return if (code in codes) "file:///android_asset/$ASSETS_DIR/$code.gif" else "$BASE_URL$code.gif"
    }

    /**
     * Two loaders: one plays GIFs, the other shows their first frame only. Separate memory caches
     * (a still frame and an animation of the same file must not stand in for each other), one disk
     * cache for the downloaded files.
     */
    fun loaders(context: Context): Pair<ImageLoader, ImageLoader> = loaders ?: synchronized(this) {
        loaders ?: run {
            val app = context.applicationContext
            val disk = DiskCache.Builder().directory(app.cacheDir.resolve("effect_previews").toOkioPath()).build()
            val animated = ImageLoader.Builder(app)
                .components { add(AnimatedImageDecoder.Factory()) }
                .diskCache { disk }
                .build()
            val still = ImageLoader.Builder(app)
                .diskCache { disk }
                .build()
            (animated to still).also { loaders = it }
        }
    }
}

/** An effect's preview: playing, or its first frame. */
@Composable
fun EffectPreview(code: String, playing: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (animated, still) = remember { EffectPreviews.loaders(context) }
    val model = remember(code) { EffectPreviews.model(context, code) }
    // Coil keys on the model, not the loader: recreate the image when playing flips
    key(playing) {
        AsyncImage(
            model = model,
            imageLoader = if (playing) animated else still,
            contentDescription = null,
            // Pixel art: no smoothing when scaled
            filterQuality = FilterQuality.None,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
        )
    }
}
