package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.callMethod
import io.github.soclear.oneuix.hook.util.callStaticMethod
import io.github.soclear.oneuix.hook.util.currentApplication
import io.github.soclear.oneuix.hook.util.get
import io.github.soclear.oneuix.hook.util.set
import io.github.soclear.oneuix.hook.util.xlog
import java.util.Collections
import java.util.WeakHashMap

object Gallery {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun supportAllSettings() {
        if (param.packageName != Package.GALLERY) return
        val featureClass = runCatching {
            param.classLoader.loadClass("com.samsung.android.gallery.support.utils.Features")
        }.getOrNull() ?: return

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
            // 音频橡皮擦
            "SUPPORT_AUDIO_ERASER",
        )

        for (feature in featureList) {
            val featureInstance = runCatching {
                featureClass[feature]
            }.getOrNull() ?: continue

            try {
                featureInstance.javaClass.declaredMethods
                    .filter { it.name == "getEnabling" }
                    .forEach { method ->
                        xposedModule.hook(method).intercept { true }
                    }
            } catch (t: Throwable) {
                xlog(t)
            }
        }

        // 各设置项在 com.samsung.android.gallery.settings.ui.SettingFragment 的 initPreference
        try {
            val settingPreferenceClass = param.classLoader.loadClass(
                "com.samsung.android.gallery.module.settings.SettingPreference"
            )
            val trashClass = settingPreferenceClass["Trash"]?.javaClass
            trashClass?.declaredMethods
                ?.filter { it.name == "support" && it.parameterTypes.contentEquals(arrayOf(Context::class.java)) }
                ?.forEach { method ->
                    xposedModule.hook(method).intercept { true }
                }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun supportSharedAlbumsInHide() {
        if (param.packageName != Package.GALLERY) return
        val hideAlbumsLocation = "location://albums/hide"
        val sharedAlbumsLocation = "location://sharing/albums/spaces"
        val sharedAlbumPrefs = "oneuix_gallery"
        val hiddenSharedAlbumIds = "hidden_shared_album_ids"

        val sharedAlbums = Collections.synchronizedMap(LinkedHashMap<String, Any>())
        val sharingDataSets = Collections.synchronizedSet(
            Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
        )

        fun getHiddenSharedAlbumIds(): Set<String> {
            return currentApplication()
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
            return currentApplication()
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
                    val data = dataSet["mData"] as? ArrayList<Any> ?: return@forEach

                    @Suppress("UNCHECKED_CAST")
                    val spaces = (dataSet["mChildDataMap"]
                            as? Map<String, ArrayList<Any>>)?.get(sharedAlbumsLocation)
                        ?: return@forEach

                    listOf(data, spaces).forEach { list ->
                        list.removeAll { item ->
                            (mediaItemMdeClass.callStaticMethod(
                                "getSpaceId",
                                item
                            ) as? String) in albums
                        }
                        list.addAll(visibleAlbums)
                    }
                    dataSet["mDataCount"] = data.size
                    dataSet.callMethod("notifyChanged")
                } catch (t: Throwable) {
                    xlog(t)
                }
            }
        }

        val classLoader = param.classLoader

        try {
            val mediaItemClass = classLoader.loadClass(
                "com.samsung.android.gallery.module.data.MediaItem"
            )
            val mediaItemMdeClass = classLoader.loadClass(
                "com.samsung.android.gallery.module.data.MediaItemMde"
            )
            val mediaDataMdeSpaceClass = classLoader.loadClass(
                "com.samsung.android.gallery.module.dataset.MediaDataMdeSpace"
            )
            val mediaDataNestedClass = classLoader.loadClass(
                "com.samsung.android.gallery.module.dataset.MediaDataNested"
            )
            val albumHelperClass = classLoader.loadClass(
                "com.samsung.android.gallery.module.album.AlbumHelper"
            )

            fun isSharing(item: Any): Boolean =
                item.callMethod("getStorageType")?.toString() == "Sharing"

            fun getSpaceId(item: Any): String? =
                mediaItemMdeClass.callStaticMethod("getSpaceId", item) as? String

            mediaDataMdeSpaceClass.declaredConstructors.forEach { constructor ->
                xposedModule.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    sharingDataSets.add(chain.thisObject)
                    result
                }
            }

            val swapInternalMethod = mediaDataMdeSpaceClass.declaredMethods.firstOrNull {
                it.name == "swapInternal" && it.parameterTypes.size == 6
            }
            if (swapInternalMethod != null) {
                xposedModule.hook(swapInternalMethod).intercept { chain ->
                    @Suppress("UNCHECKED_CAST")
                    val data = chain.args[1] as ArrayList<Any>

                    @Suppress("UNCHECKED_CAST")
                    val spaceMap = chain.args[2] as HashMap<String, Any>

                    @Suppress("UNCHECKED_CAST")
                    val childDataMap = chain.args[4] as HashMap<String, ArrayList<Any>>
                    val spaces = childDataMap[sharedAlbumsLocation]
                    if (spaces != null) {
                        val hiddenIds = getHiddenSharedAlbumIds()

                        synchronized(sharedAlbums) {
                            spaces.forEach { item ->
                                val spaceId = getSpaceId(item) ?: return@forEach
                                item.callMethod("setAlbumHide", spaceId in hiddenIds)
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
                        chain.args[5] = data.size
                    }
                    chain.proceed()
                }
            }

            val createFullListMethod = mediaDataNestedClass.declaredMethods.firstOrNull {
                it.name == "createFullList" && it.parameterTypes.size == 2
            }
            if (createFullListMethod != null) {
                xposedModule.hook(createFullListMethod).intercept { chain ->
                    val result = chain.proceed()
                    if (chain.thisObject.callMethod("getLocationKey") == hideAlbumsLocation) {
                        @Suppress("UNCHECKED_CAST")
                        val data = result as? ArrayList<Any>
                        if (data != null) {
                            val hiddenIds = getHiddenSharedAlbumIds()
                            synchronized(sharedAlbums) {
                                sharedAlbums.forEach { (spaceId, item) ->
                                    item.callMethod("setAlbumHide", spaceId in hiddenIds)
                                    data.add(item)
                                }
                            }
                        }
                    }
                    result
                }
            }

            val updateAlbumsHideStateMethod = albumHelperClass.declaredMethods.firstOrNull {
                it.name == "updateAlbumsHideState" && it.parameterTypes.contentEquals(arrayOf(mediaItemClass))
            }
            if (updateAlbumsHideStateMethod != null) {
                xposedModule.hook(updateAlbumsHideStateMethod).intercept { chain ->
                    val item = chain.args[0] ?: return@intercept chain.proceed()
                    if (!isSharing(item)) return@intercept chain.proceed()
                    val spaceId = getSpaceId(item) ?: return@intercept chain.proceed()
                    val hidden = item.callMethod("isAlbumHide") as? Boolean ?: return@intercept chain.proceed()

                    if (setSharedAlbumHidden(spaceId, hidden)) {
                        refreshSharingData(mediaItemMdeClass)
                        1
                    } else {
                        chain.proceed()
                    }
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hideVideoEditorStudio() {
        if (param.packageName != Package.GALLERY) return
        try {
            val studioClass = param.classLoader.loadClass(
                $$"com.samsung.android.gallery.app.ui.container.menu.BottomMenuItem$Studio"
            )
            val method = studioClass.getDeclaredMethod("support", Context::class.java)
            xposedModule.hook(method).intercept { false }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
