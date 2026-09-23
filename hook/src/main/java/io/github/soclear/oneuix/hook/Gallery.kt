package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.currentContext
import io.github.soclear.oneuix.hook.util.reflect
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
                featureClass.reflect[feature]
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
            val trashClass = settingPreferenceClass.reflect["Trash"]?.javaClass
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

        fun getPrefs(): SharedPreferences? {
            return currentContext().getSharedPreferences(sharedAlbumPrefs, Context.MODE_PRIVATE)
        }

        fun getHiddenSharedAlbumIds(): Set<String> {
            return getPrefs()
                ?.getStringSet(hiddenSharedAlbumIds, emptySet())
                .orEmpty()
        }

        @SuppressLint("UseKtx") // KTX edit discards the synchronous commit result.
        fun setSharedAlbumHidden(spaceId: String, hidden: Boolean): Boolean {
            val prefs = getPrefs() ?: return false
            val hiddenIds = getHiddenSharedAlbumIds().toMutableSet()
            if (hidden) {
                hiddenIds.add(spaceId)
            } else {
                hiddenIds.remove(spaceId)
            }
            return prefs
                .edit()
                .putStringSet(hiddenSharedAlbumIds, hiddenIds)
                .commit()
        }

        afterAttach {
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
                    runCatching { item.reflect("getStorageType")?.toString() }.getOrNull() == "Sharing"

                fun getSpaceId(item: Any): String? =
                    runCatching { mediaItemMdeClass.reflect.callAs<String>("getSpaceId", item) }.getOrNull()

                fun getSpacesFromChildDataMap(childDataMap: Any?): MutableList<Any>? {
                    val map = childDataMap as? Map<*, *> ?: return null
                    @Suppress("UNCHECKED_CAST")
                    return (map as Map<Any, Any>)[sharedAlbumsLocation] as? MutableList<Any>
                }

                fun refreshSharingData() {
                    val hiddenIds = getHiddenSharedAlbumIds()
                    val albums = synchronized(sharedAlbums) { LinkedHashMap(sharedAlbums) }
                    val visibleAlbums = albums.filterKeys { it !in hiddenIds }.values
                    val dataSets = synchronized(sharingDataSets) {
                        sharingDataSets.toList()
                    }
                    dataSets.forEach { dataSet ->
                        try {
                            val data: MutableList<Any> = dataSet.reflect.getAs("mData") ?: return@forEach
                            val spaces = getSpacesFromChildDataMap(dataSet.reflect["mChildDataMap"]) ?: return@forEach

                            listOf(data, spaces).forEach { list ->
                                list.removeAll { item ->
                                    getSpaceId(item) in albums
                                }
                                list.addAll(visibleAlbums)
                            }
                            dataSet.reflect["mDataCount"] = data.size
                            runCatching { dataSet.reflect("notifyChanged") }
                        } catch (t: Throwable) {
                            xlog(t)
                        }
                    }
                }

                mediaDataMdeSpaceClass.declaredConstructors.forEach { constructor ->
                    xposedModule.hook(constructor).intercept { chain ->
                        val result = chain.proceed()
                        chain.thisObject?.let {
                            sharingDataSets.add(it)
                        }
                        result
                    }
                }

                fun handleSwap(
                    data: MutableList<Any>?,
                    spaceMap: MutableMap<String, Any>?,
                    childDataMap: Any?,
                    onUpdateDataCount: (Int) -> Unit
                ) {
                    val spaces = getSpacesFromChildDataMap(childDataMap)
                    if (spaces != null && data != null && spaceMap != null) {
                        val hiddenIds = getHiddenSharedAlbumIds()

                        synchronized(sharedAlbums) {
                            spaces.forEach { item ->
                                val spaceId = getSpaceId(item) ?: return@forEach
                                runCatching { item.reflect("setAlbumHide", spaceId in hiddenIds) }
                                sharedAlbums[spaceId] = item
                            }
                        }

                        if (hiddenIds.isNotEmpty()) {
                            data.removeAll { item ->
                                isSharing(item) && getSpaceId(item) in hiddenIds
                            }
                            spaces.removeAll { item -> getSpaceId(item) in hiddenIds }
                            hiddenIds.forEach { spaceMap.remove(it) }
                        }
                        onUpdateDataCount(data.size)
                    }
                }

                val swapInternalMethod = mediaDataMdeSpaceClass.reflect.findMethod {
                    it.name == "swapInternal" && it.parameterTypes.size == 6
                }
                if (swapInternalMethod != null) {
                    xposedModule.hook(swapInternalMethod).intercept { chain ->
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val data = chain.args.getOrNull(1) as? MutableList<Any>

                            @Suppress("UNCHECKED_CAST")
                            val spaceMap = chain.args.getOrNull(2) as? MutableMap<String, Any>
                            val childDataMap = chain.args.getOrNull(4)
                            var updated = false
                            val newArgs = chain.args.toTypedArray()
                            handleSwap(data, spaceMap, childDataMap) { newCount ->
                                newArgs[5] = newCount
                                updated = true
                            }
                            if (updated) chain.proceed(newArgs) else chain.proceed()
                        } catch (t: Throwable) {
                            xlog(t)
                            chain.proceed()
                        }
                    }
                }

                val swapLambdaMethod = mediaDataMdeSpaceClass.reflect.findMethod {
                    it.parameterTypes.size == 9 &&
                            it.parameterTypes[2] == java.util.ArrayList::class.java &&
                            it.parameterTypes[5] == java.util.HashMap::class.java
                }
                if (swapLambdaMethod != null) {
                    xposedModule.hook(swapLambdaMethod).intercept { chain ->
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val data = chain.args.getOrNull(2) as? MutableList<Any>

                            @Suppress("UNCHECKED_CAST")
                            val spaceMap = chain.args.getOrNull(3) as? MutableMap<String, Any>
                            val childDataMap = chain.args.getOrNull(5)
                            var updated = false
                            val newArgs = chain.args.toTypedArray()
                            handleSwap(data, spaceMap, childDataMap) { newCount ->
                                newArgs[6] = newCount
                                updated = true
                            }
                            if (updated) chain.proceed(newArgs) else chain.proceed()
                        } catch (t: Throwable) {
                            xlog(t)
                            chain.proceed()
                        }
                    }
                }

                val createFullListMethod = mediaDataNestedClass.reflect.findMethod {
                    it.name == "createFullList" && it.parameterTypes.size == 2
                }
                if (createFullListMethod != null) {
                    xposedModule.hook(createFullListMethod).intercept { chain ->
                        val result = chain.proceed()
                        val locationKey = runCatching {
                            chain.thisObject?.reflect?.callAs<String>("getLocationKey")
                        }.getOrNull()
                        if (locationKey == hideAlbumsLocation) {
                            @Suppress("UNCHECKED_CAST")
                            val data = result as? MutableList<Any>

                            @Suppress("UNCHECKED_CAST")
                            val fullListArg = chain.args.getOrNull(1) as? MutableList<Any>
                            val hiddenIds = getHiddenSharedAlbumIds()

                            if (sharedAlbums.isEmpty()) {
                                val dataSets = synchronized(sharingDataSets) { sharingDataSets.toList() }
                                for (dataSet in dataSets) {
                                    val spaces = getSpacesFromChildDataMap(dataSet.reflect["mChildDataMap"])
                                    if (!spaces.isNullOrEmpty()) {
                                        synchronized(sharedAlbums) {
                                            spaces.forEach { item ->
                                                val spaceId = getSpaceId(item) ?: return@forEach
                                                runCatching { item.reflect("setAlbumHide", spaceId in hiddenIds) }
                                                sharedAlbums[spaceId] = item
                                            }
                                        }
                                        break
                                    }
                                }
                            }

                            if (data != null) {
                                synchronized(sharedAlbums) {
                                    sharedAlbums.forEach { (spaceId, item) ->
                                        runCatching { item.reflect("setAlbumHide", spaceId in hiddenIds) }
                                        if (!data.contains(item)) {
                                            data.add(item)
                                        }
                                        if (fullListArg != null && !fullListArg.contains(item)) {
                                            fullListArg.add(item)
                                        }
                                    }
                                }
                            }
                        }
                        result
                    }
                }

                val updateAlbumsHideStateMethod = albumHelperClass.reflect.findMethod {
                    it.name == "updateAlbumsHideState" && it.parameterTypes.contentEquals(arrayOf(mediaItemClass))
                }
                if (updateAlbumsHideStateMethod != null) {
                    xposedModule.hook(updateAlbumsHideStateMethod).intercept { chain ->
                        val item = chain.args.firstOrNull() ?: return@intercept chain.proceed()
                        val isShare = isSharing(item)
                        val spaceId = getSpaceId(item)
                        if (!isShare) return@intercept chain.proceed()
                        if (spaceId == null) return@intercept chain.proceed()
                        val hidden = (runCatching { item.reflect.callAs<Boolean>("isAlbumHide") }.getOrNull())
                            ?: return@intercept chain.proceed()

                        if (setSharedAlbumHidden(spaceId, hidden)) {
                            refreshSharingData()
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
