package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.hook.util.currentApplication
import io.github.soclear.oneuix.hook.util.xlog

@SuppressLint("PrivateApi")
object Other {
    context(xposedModule: XposedModule)
    fun disableWritingToolkit() {
        try {
            xposedModule.hook(TextView::class.java.getDeclaredMethod("isDisableWritingToolkit"))
                .intercept { true }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hideScreenshotDetection() {
        val screenshotPath = Regex("screen[ _-]?(shot|cap)|截[屏图]", RegexOption.IGNORE_CASE)
        val screenshotUris = LruCache<Uri, Boolean>(128)

        fun isScreenshot(resolver: ContentResolver, uri: Uri): Boolean {
            val collection = uri.pathSegments.getOrNull(1)
            if (uri.authority != "media" || collection !in listOf("images", "file") ||
                uri.lastPathSegment?.toLongOrNull() == null
            ) return false
            // 缓存已识别的截图 URI，以便过滤记录删除后的通知；未知图片始终放行。
            val screenshot = runCatching {
                resolver.query(uri, arrayOf("_display_name", "relative_path"), null, null, null)?.use {
                    if (it.moveToFirst()) screenshotPath.containsMatchIn("${it.getString(1)}${it.getString(0)}")
                    else null
                }
            }.getOrNull()
            if (screenshot != null) {
                if (screenshot) screenshotUris.put(uri, true) else screenshotUris.remove(uri)
            }
            return screenshot ?: (screenshotUris[uri] == true)
        }

        if (Build.VERSION.SDK_INT >= 34) {
            runCatching {
                val observer = param.classLoader.loadClass(
                    "android.app.ScreenCaptureCallbackHandler\$ScreenCaptureObserver"
                )
                xposedModule.hook(observer.getDeclaredMethod("onScreenCaptured")).intercept { null }
            }.onFailure { xlog(it) }
        }

        // 在通知分发前只过滤截图 URI；监听注册和相册查询保持完整。
        val transport = param.classLoader.loadClass("android.database.ContentObserver\$Transport")
        xposedModule.hook(transport.getDeclaredMethod(
            "onChangeEtc", Boolean::class.javaPrimitiveType, Array<Uri>::class.java,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        )).intercept { chain ->
            val resolver = currentApplication()?.contentResolver ?: return@intercept chain.proceed()
            @Suppress("UNCHECKED_CAST")
            val uris = chain.args[1] as Array<Uri?>
            val visible = uris.filterNot { it != null && isScreenshot(resolver, it) }
            when {
                visible.size == uris.size -> chain.proceed()
                visible.isEmpty() -> null
                else -> chain.proceed(chain.args.toTypedArray().apply { this[1] = visible.toTypedArray() })
            }
        }
    }
}
