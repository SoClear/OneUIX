package io.github.soclear.oneuix.hook

import android.app.Activity
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.hook.util.afterAttachTry

object Wallpaper {
    private const val LIBRARY_ACTIVITY =
        "com.samsung.android.app.dressroom.presentation.library.ui.WallpaperLibraryActivity"
    private const val MENU_ID_LIVE_WALLPAPER = 0x4C575001
    private const val ACTION_LIVE_WALLPAPER_CHOOSER = "android.service.wallpaper.LIVE_WALLPAPER_CHOOSER"

    /**
     * 三星壁纸应用在国行系统上，三点菜单里没有“动态壁纸”入口（菜单资源里没有，处理代码也被删了）。
     * 这里在菜单创建后补一项，点击时打开系统的动态壁纸选择器。不改 APK，只依赖
     * WallpaperLibraryActivity 里两个标准的菜单回调，更新壁纸应用后一般无需适配。
     */
    context(xposedModule: XposedModule)
    fun restoreLiveWallpaperEntry() = afterAttachTry {
        val activityClass = classLoader.loadClass(LIBRARY_ACTIVITY)

        xposedModule.hook(
            activityClass.getDeclaredMethod("onCreateOptionsMenu", Menu::class.java)
        ).intercept { chain ->
            val result = chain.proceed()
            runCatching {
                val activity = chain.thisObject as Activity
                val menu = chain.args[0] as Menu
                // 菜单为空说明库页面当前不显示三点菜单，此时不添加
                if (menu.size() > 0 && menu.findItem(MENU_ID_LIVE_WALLPAPER) == null) {
                    val titleRes = activity.resources.getIdentifier(
                        "options_menu_title_live_wallpapers", "string", activity.packageName
                    )
                    val title = if (titleRes != 0) activity.getString(titleRes) else "Live wallpapers"
                    menu.add(Menu.NONE, MENU_ID_LIVE_WALLPAPER, Menu.NONE, title)
                }
            }
            result
        }

        xposedModule.hook(
            activityClass.getDeclaredMethod("onOptionsItemSelected", MenuItem::class.java)
        ).intercept { chain ->
            val item = chain.args[0] as MenuItem
            if (item.itemId == MENU_ID_LIVE_WALLPAPER) {
                val activity = chain.thisObject as Activity
                runCatching { activity.startActivity(Intent(ACTION_LIVE_WALLPAPER_CHOOSER)) }
                true
            } else {
                chain.proceed()
            }
        }
    }
}
