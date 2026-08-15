package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.Context
import android.database.Cursor
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getStaticObjectField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setIntField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import java.util.Collections
import java.util.LinkedHashMap
import java.util.WeakHashMap

object Gallery {
    fun supportAllSettings(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.GALLERY) return
        val featureClass = findClass(
            "com.samsung.android.gallery.support.utils.Features",
            loadPackageParam.classLoader
        )
        // 设置项见反编译后的 SettingSearchIndexablesProvider
        val featureList = listOf(
            // 故事 -> 自动创建故事
            "SUPPORT_AUTO_CREATE_STORY",
            // 识别图片中的内容
            "SUPPORT_CMH_PROVIDER_PERMISSION",
            // 回收站
            "SUPPORT_TRASH",
            // 分享时转换 HEIF 图片
            "SUPPORT_HEIF_CONVERSION",
            // 分享时转换 HDR10+ 视频
            "SUPPORT_HDR10PLUS_CONVERSION",
            // One UI 7 音频橡皮擦（视频编辑器）
            "SUPPORT_AUDIO_ERASER",
            // One UI 8 音频橡皮擦（相册内）
            "SUPPORT_AUDIO_ERASER_IN_GALLERY",
        )
        val returnTrue = returnConstant(true)

        for (feature in featureList) {
            val featureInstance = try {
                getStaticObjectField(featureClass, feature)
            } catch (_: NoSuchFieldError) {
                continue
            }
            try {
                findAndHookMethod(featureInstance.javaClass, "getEnabling", returnTrue)
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
        }

        // 各设置项见 com.samsung.android.gallery.settings.ui.SettingFragment 的 initPreference

        try {
            val settingPreferenceClass = findClass(
                "com.samsung.android.gallery.module.settings.SettingPreference",
                loadPackageParam.classLoader
            )
            listOf("Trash", "AudioEraser").forEach { preference ->
                try {
                    val preferenceClass =
                        getStaticObjectField(settingPreferenceClass, preference).javaClass
                    findAndHookMethod(
                        preferenceClass,
                        "support",
                        Context::class.java,
                        returnTrue
                    )
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun supportSharedAlbumsInHide(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.GALLERY) return
        val hideAlbumsLocation = "location://albums/hide"
        val sharedAlbumsLocation = "location://sharing/albums/spaces"
        val sharedAlbumPrefs = "oneuix_gallery"
        val hiddenSharedAlbumIds = "hidden_shared_album_ids"

        val sharedAlbums = Collections.synchronizedMap(LinkedHashMap<String, Any>())
        val sharingDataSets = Collections.synchronizedSet(
            Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
        )

        fun getHiddenSharedAlbumIds(): Set<String> {
            return AndroidAppHelper.currentApplication()
                .getSharedPreferences(sharedAlbumPrefs, Context.MODE_PRIVATE)
                .getStringSet(hiddenSharedAlbumIds, emptySet())
                .orEmpty()
        }

        @SuppressLint("UseKtx") // KTX edit discards the synchronous commit result.
        fun setSharedAlbumHidden(spaceId: String, hidden: Boolean): Boolean {
            val hiddenIds = getHiddenSharedAlbumIds().toMutableSet()
            if (hidden) {
                hiddenIds.add(spaceId)
            } else {
                hiddenIds.remove(spaceId)
            }
            return AndroidAppHelper.currentApplication()
                .getSharedPreferences(sharedAlbumPrefs, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(hiddenSharedAlbumIds, hiddenIds)
                .commit()
        }

        fun refreshSharingData(mediaItemMdeClass: Class<*>) {
            val hiddenIds = getHiddenSharedAlbumIds()
            val albums = synchronized(sharedAlbums) { LinkedHashMap(sharedAlbums) }
            val visibleAlbums = albums.filterKeys { it !in hiddenIds }.values
            val dataSets = synchronized(sharingDataSets) {
                sharingDataSets.toList()
            }
            dataSets.forEach { dataSet ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val data = getObjectField(dataSet, "mData") as ArrayList<Any>

                    @Suppress("UNCHECKED_CAST")
                    val spaces = (getObjectField(dataSet, "mChildDataMap")
                            as Map<String, ArrayList<Any>>)[sharedAlbumsLocation]
                        ?: return@forEach

                    listOf(data, spaces).forEach { list ->
                        list.removeAll { item ->
                            (callStaticMethod(
                                mediaItemMdeClass,
                                "getSpaceId",
                                item
                            ) as? String) in albums
                        }
                        list.addAll(visibleAlbums)
                    }
                    setIntField(dataSet, "mDataCount", data.size)
                    callMethod(dataSet, "notifyChanged")
                } catch (t: Throwable) {
                    XposedBridge.log(t)
                }
            }
        }

        val classLoader = loadPackageParam.classLoader

        try {
            val mediaItemClass = findClass(
                "com.samsung.android.gallery.module.data.MediaItem",
                classLoader
            )
            val mediaItemMdeClass = findClass(
                "com.samsung.android.gallery.module.data.MediaItemMde",
                classLoader
            )
            val mediaDataMdeSpaceClass = findClass(
                "com.samsung.android.gallery.module.dataset.MediaDataMdeSpace",
                classLoader
            )
            val mediaDataNestedClass = findClass(
                "com.samsung.android.gallery.module.dataset.MediaDataNested",
                classLoader
            )
            val albumHelperClass = findClass(
                "com.samsung.android.gallery.module.album.AlbumHelper",
                classLoader
            )

            fun isSharing(item: Any): Boolean =
                callMethod(item, "getStorageType")?.toString() == "Sharing"

            fun getSpaceId(item: Any): String? =
                callStaticMethod(mediaItemMdeClass, "getSpaceId", item) as? String

            hookAllConstructors(mediaDataMdeSpaceClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    sharingDataSets.add(param.thisObject)
                }
            })

            findAndHookMethod(
                mediaDataMdeSpaceClass,
                "swapInternal",
                Array<Cursor>::class.java,
                ArrayList::class.java,
                HashMap::class.java,
                HashMap::class.java,
                HashMap::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val data = param.args[1] as ArrayList<Any>

                        @Suppress("UNCHECKED_CAST")
                        val spaceMap = param.args[2] as HashMap<String, Any>

                        @Suppress("UNCHECKED_CAST")
                        val childDataMap = param.args[4] as HashMap<String, ArrayList<Any>>
                        val spaces = childDataMap[sharedAlbumsLocation] ?: return
                        val hiddenIds = getHiddenSharedAlbumIds()

                        synchronized(sharedAlbums) {
                            spaces.forEach { item ->
                                val spaceId = getSpaceId(item) ?: return@forEach
                                callMethod(item, "setAlbumHide", spaceId in hiddenIds)
                                sharedAlbums[spaceId] = item
                            }
                        }

                        if (hiddenIds.isNotEmpty()) {
                            data.removeAll { item ->
                                isSharing(item) && getSpaceId(item) in hiddenIds
                            }
                            spaces.removeAll { item -> getSpaceId(item) in hiddenIds }
                            hiddenIds.forEach(spaceMap::remove)
                        }
                        param.args[5] = data.size
                    }
                }
            )

            findAndHookMethod(
                mediaDataNestedClass,
                "createFullList",
                Array<Cursor>::class.java,
                ArrayList::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (callMethod(param.thisObject, "getLocationKey") != hideAlbumsLocation) {
                            return
                        }
                        @Suppress("UNCHECKED_CAST")
                        val data = param.result as? ArrayList<Any> ?: return
                        val hiddenIds = getHiddenSharedAlbumIds()

                        synchronized(sharedAlbums) {
                            sharedAlbums.forEach { (spaceId, item) ->
                                callMethod(item, "setAlbumHide", spaceId in hiddenIds)
                                data.add(item)
                            }
                        }
                    }
                }
            )

            findAndHookMethod(
                albumHelperClass,
                "updateAlbumsHideState",
                mediaItemClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val item = param.args[0] ?: return
                        if (!isSharing(item)) return
                        val spaceId = getSpaceId(item) ?: return
                        val hidden = callMethod(item, "isAlbumHide") as? Boolean ?: return

                        if (setSharedAlbumHidden(spaceId, hidden)) {
                            param.result = 1
                            refreshSharingData(mediaItemMdeClass)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun hideVideoEditorStudio(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.processName != Package.GALLERY) return
        try {
            findAndHookMethod(
                $$"com.samsung.android.gallery.app.ui.container.menu.BottomMenuItem$Studio",
                loadPackageParam.classLoader,
                "support",
                Context::class.java,
                returnConstant(false)
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}
