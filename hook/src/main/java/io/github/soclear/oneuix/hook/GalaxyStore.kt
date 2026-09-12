package io.github.soclear.oneuix.hook

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge.hookMethod
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookConstructor
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlinx.serialization.Serializable
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.MatchType
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier


object GalaxyStore {
    fun blockGalaxyStoreAds(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.STORE) {
            return
        }
        afterAttach {
            val hookConfig = getHookConfig { getHookConfigFromDexKit() }
            if (hookConfig != null) {
                hook(classLoader, hookConfig)
            }
        }
    }


    @Serializable
    private data class GalaxyStoreHookConfig(
        override val versionCode: Long,
        val prepareRecommendPopupOnDownloading: Set<String>,
        val isWebViewPopupHideDay: String,
        val requestMapPutMethod: String,
        val getAdDataGroupParentMethod: String,
        val checkAge: String,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): GalaxyStoreHookConfig? {
        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val prepareRecommendPopupOnDownloading = bridge.findClass {
                searchPackages("com.sec.android.app.samsungapps.detail.activity")
                matcher {
                    modifiers(Modifier.PUBLIC, MatchType.Equals)
                }
            }.findMethod {
                matcher { name = "prepareRecommendPopupOnDownloading" }
            }
            if (prepareRecommendPopupOnDownloading.isEmpty()) {
                return null
            }

            val isWebViewPopupHideDay = bridge.findClass {
                searchPackages("com.samsung.android.game.cloudgame.sdk")
                matcher {
                    addInterface("com.sec.android.app.commonlib.doc.DataExchanger")
                }
            }.findMethod {
                matcher { name = "isWebViewPopupHideDay" }
            }.singleOrNull() ?: return null

            val requestMapPutMethod = bridge.findClass {
                searchPackages("com.samsung.android.game.cloudgame.sdk")
                matcher {
                    modifiers = Modifier.PUBLIC
                    superClass = "java.lang.Object"
                    usingStrings(
                        "IP20-SHELL",
                        "Occurred NoSuchAlgorithmException. So will be return default value(yyyyMMddHH). GalaxyApps's hashValue : ",
                    )
                }
            }.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    returnType = "void"
                    paramTypes(
                        String::class.java,
                        String::class.java,
                        Boolean::class.javaPrimitiveType
                    )
                    usingEqStrings("")
                }
            }.singleOrNull {
                "IMEI" !in it.usingStrings
            } ?: return null

            val adInventoryManager = bridge.getClassData(
                "com.sec.android.app.samsungapps.curate.ad.AdInventoryGroup"
            ) ?: return null

            val getAdDataGroupParentMethod = adInventoryManager.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    returnType = "com.sec.android.app.samsungapps.curate.ad.AdDataGroupParent"
                    paramTypes(List::class.java)
                    usingStrings("ListPortWithBanner", "ListLandSearchPage")
                }
            }.singleOrNull() ?: return null

            val checkAge = bridge.findClass {
                searchPackages("com.samsung.android.game.cloudgame.sdk")
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    addInterface("com.sec.android.app.commonlib.realnameage.IAgeLimitChecker")
                    superClass = "java.lang.Object"
                }
            }.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    returnType = "void"
                    name = "check"
                }
            }.singleOrNull() ?: return null

            return GalaxyStoreHookConfig(
                versionCode = packageManager.getPackageInfo(packageName, 0).longVersionCode,
                prepareRecommendPopupOnDownloading = prepareRecommendPopupOnDownloading
                    .map { it.toDexMethod().serialize() }
                    .toSet(),
                isWebViewPopupHideDay = isWebViewPopupHideDay.toDexMethod().serialize(),
                requestMapPutMethod = requestMapPutMethod.toDexMethod().serialize(),
                getAdDataGroupParentMethod = getAdDataGroupParentMethod.toDexMethod()
                    .serialize(),
                checkAge = checkAge.toDexMethod().serialize(),
            )
        }
    }


    private fun hook(classLoader: ClassLoader, hookConfig: GalaxyStoreHookConfig) {
        val returnNull = returnConstant(null)
        // 去除详情页点击安装后的的推荐弹窗
        hookConfig.prepareRecommendPopupOnDownloading.forEach {
            hookMethod(DexMethod(it).getMethodInstance(classLoader), returnNull)
        }

        // 去除首页弹窗
        hookMethod(
            DexMethod(hookConfig.isWebViewPopupHideDay).getMethodInstance(classLoader),
            returnConstant(true)
        )

        hookMethod(
            DexMethod(hookConfig.requestMapPutMethod).getMethodInstance(classLoader),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    when (param.args[0]) {
                        // 禁止上传隐私
                        "pengtaiInfo", "tencentReportInfoSupport", "chinaInfo" -> {
                            param.result = null
                        }

                        // 禁止加载广告
                        "adInfoList" -> {
                            param.args[1] = "N"
                        }
                    }
                }
            }
        )

        // 拦截广告加载完成回调
        findAndHookMethod(
            "com.sec.android.app.samsungapps.slotpage.GalaxyAppsMainActivity",
            classLoader,
            "onAdAvailable",
            $$"com.sec.android.app.samsungapps.curate.ad.AdInventoryManager$PLATFORM",
            returnNull
        )

        // 清空广告列表
        findAndHookConstructor(
            "com.sec.android.app.samsungapps.curate.ad.AdInventoryGroupSAP",
            classLoader,
            "java.util.List",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val itemList = getObjectField(
                        param.thisObject,
                        "itemList"
                    ) as ArrayList<*>
                    itemList.clear()
                }
            }
        )

        // 替换为空的广告组
        hookMethod(
            DexMethod(hookConfig.getAdDataGroupParentMethod).getMethodInstance(classLoader),
            object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam?): Any? {
                    val adDataGroupParentClass = XposedHelpers.findClass(
                        "com.sec.android.app.samsungapps.curate.ad.AdDataGroupParent",
                        classLoader
                    )
                    return adDataGroupParentClass.constructors.first {
                        it.parameterCount == 0
                    }.newInstance()
                }
            }
        )

        // 去除年龄验证
        hookMethod(
            DexMethod(hookConfig.checkAge).getMethodInstance(classLoader),
            object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    param.args.last()?.let {
                        callMethod(it, "onResult", true)
                    }
                    return null
                }
            }
        )
    }
}
