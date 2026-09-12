package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.telephony.ServiceState
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.findFieldIfExists
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.common.Package
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap
import kotlin.collections.any
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.get
import kotlin.collections.ifEmpty
import kotlin.collections.set

@SuppressLint("StaticFieldLeak")
object ESIM {
    private const val PHYSICAL_ESIM_ADAPTER_SIM_1 = 0
    private const val PHYSICAL_ESIM_ADAPTER_SIM_2 = 1
    private const val PHYSICAL_ESIM_ADAPTER_BOTH = 2
    private val trackedMobileViewSlots: MutableMap<View, Int> =
        Collections.synchronizedMap(WeakHashMap())
    private val hiddenMobileViews: MutableSet<View> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))
    private val unavailableCarrierSlots = mutableSetOf<Int>()
    private val telephonyManagersBySubId = mutableMapOf<Int, TelephonyManager>()
    private val unavailableCarrierTexts: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf())
    private var physicalEsimAdapterContext: Context? = null

    @Volatile
    private var unavailableCarrierTextsLoaded = false

    private val unavailableCarrierTextResourceNames = listOf(
        "emergency_calls_only",
        "lockscreen_carrier_default",
        "kg_emergency_calls_only",
        "keyguard_emergency_calls_only",
        "keyguard_carrier_default",
        "status_bar_no_service",
        "status_bar_network_name_no_service",
        "mobile_network_no_service",
        "no_service"
    )

    private val unavailableCarrierTextFallbacks = setOf(
        "emergency calls only",
        "no service"
    )

    fun workaroundPhysicalEsimAdapter(loadPackageParam: LoadPackageParam, simSlotMode: Int) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        val selectedSlots = selectedPhysicalEsimAdapterSlots(simSlotMode)

        try {
            hookAllMethods(
                findClass(
                    "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView",
                    loadPackageParam.classLoader
                ),
                "constructAndBind",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args[0] as? Context
                        updatePhysicalEsimAdapterContext(context)
                        updateUnavailableCarrierTexts(context)
                        val viewModel = param.args[3] ?: return
                        val subId = getMobileViewModelSubId(viewModel)
                        val slot = subId
                            ?.let(SubscriptionManager::getSlotIndex)
                            ?.takeIf { it >= 0 }
                            ?: getMobileViewModelSlot(viewModel)
                            ?: return
                        if (slot in selectedSlots) {
                            subId?.let {
                                updateCarrierSlotAvailabilityFromSubId(
                                    subId = it,
                                    slot = slot,
                                    context = context
                                )
                            }
                            trackMobileView(param.result as? View, slot, selectedSlots)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView",
                loadPackageParam.classLoader,
                "setVisibleState",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val slot = trackedMobileViewSlots[param.thisObject as? View] ?: return
                        if (slot in selectedSlots && isUnavailableCarrierSlot(slot)) {
                            param.args[0] = 2
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? View ?: return
                        val slot = trackedMobileViewSlots[view] ?: return
                        applyMobileViewVisibility(view, slot, selectedSlots)
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView",
                loadPackageParam.classLoader,
                "isIconVisible",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val slot = trackedMobileViewSlots[param.thisObject as? View] ?: return
                        if (slot in selectedSlots && isUnavailableCarrierSlot(slot)) {
                            param.result = false
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        try {
            val legacyStatusBarMobileView = findClassIfExists(
                "com.android.systemui.statusbar.StatusBarMobileView",
                loadPackageParam.classLoader
            )
            if (legacyStatusBarMobileView != null) {
                findAndHookMethod(
                    legacyStatusBarMobileView,
                    "applyMobileState",
                    $$"com.android.systemui.statusbar.phone.StatusBarSignalPolicy$MobileIconState",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val state = param.args[0] ?: return
                            val slot = SubscriptionManager.getSlotIndex(getIntField(state, "subId"))
                            if (slot in selectedSlots) {
                                val view = param.thisObject as? View
                                updateCarrierSlotAvailabilityFromMobileState(
                                    state = state,
                                    slot = slot,
                                    context = view?.context
                                )
                                trackMobileView(view, slot, selectedSlots)
                            }
                        }
                    }
                )
            }
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        try {
            hookAllConstructors(
                findClass(
                    "com.android.keyguard.CarrierTextManager",
                    loadPackageParam.classLoader
                ),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.args
                            .filterIsInstance<Context>()
                            .firstOrNull()
                            ?.let { context ->
                                updatePhysicalEsimAdapterContext(context)
                                updateUnavailableCarrierTexts(context)
                            }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        try {
            findAndHookMethod(
                "com.android.keyguard.CarrierTextManager", loadPackageParam.classLoader, "postToCallback",
                $$"com.android.keyguard.CarrierTextManager$CarrierTextCallbackInfo", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        sanitizeCarrierTextCallbackInfo(
                            info = param.args[0] ?: return,
                            selectedSlots = selectedSlots,
                            context = physicalEsimAdapterContext
                        )
                        refreshTrackedMobileViews(selectedSlots)
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    private fun getMobileViewModelSlot(viewModel: Any): Int? {
        runCatching {
            val commonImpl = getObjectField(viewModel, "commonImpl")
            val slot = getIntField(commonImpl, "slotId")
            if (slot >= 0) return slot
        }

        runCatching {
            val subId = callMethod(viewModel, "getSubscriptionId") as Int
            val slot = SubscriptionManager.getSlotIndex(subId)
            if (slot >= 0) return slot
        }

        return null
    }

    private fun getMobileViewModelSubId(viewModel: Any): Int? =
        runCatching { callMethod(viewModel, "getSubscriptionId") as Int }
            .getOrNull()
            ?.takeIf(SubscriptionManager::isValidSubscriptionId)

    private fun selectedPhysicalEsimAdapterSlots(simSlotMode: Int): Set<Int> =
        when (simSlotMode) {
            PHYSICAL_ESIM_ADAPTER_SIM_1 -> setOf(PHYSICAL_ESIM_ADAPTER_SIM_1)
            PHYSICAL_ESIM_ADAPTER_BOTH -> setOf(
                PHYSICAL_ESIM_ADAPTER_SIM_1,
                PHYSICAL_ESIM_ADAPTER_SIM_2
            )

            else -> setOf(PHYSICAL_ESIM_ADAPTER_SIM_2)
        }

    private fun trackMobileView(view: View?, slot: Int, selectedSlots: Set<Int>) {
        view ?: return
        trackedMobileViewSlots[view] = slot
        applyMobileViewVisibility(view, slot, selectedSlots)
    }

    private fun applyMobileViewVisibility(view: View, slot: Int, selectedSlots: Set<Int>) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            view.post { applyMobileViewVisibility(view, slot, selectedSlots) }
            return
        }

        if (slot in selectedSlots && isUnavailableCarrierSlot(slot)) {
            hiddenMobileViews.add(view)
            view.visibility = View.GONE
        } else if (slot in selectedSlots && hiddenMobileViews.remove(view)) {
            view.visibility = View.VISIBLE
        }
    }

    private fun refreshTrackedMobileViews(selectedSlots: Set<Int>) {
        trackedMobileViewSlots.entries.toList().forEach { (view, slot) ->
            applyMobileViewVisibility(view, slot, selectedSlots)
        }
    }

    private fun isUnavailableCarrierSlot(slot: Int): Boolean =
        synchronized(unavailableCarrierSlots) { slot in unavailableCarrierSlots }

    private fun updateCarrierSlotAvailabilityFromMobileState(state: Any, slot: Int, context: Context?) {
        val unavailable = getUnavailableServiceState(context, getIntField(state, "subId"))
            ?: isUnavailableMobileStateText(state)

        synchronized(unavailableCarrierSlots) {
            if (unavailable) {
                unavailableCarrierSlots.add(slot)
            } else {
                unavailableCarrierSlots.remove(slot)
            }
        }
    }

    private fun updateCarrierSlotAvailabilityFromSubId(subId: Int, slot: Int, context: Context?) {
        val unavailable = getUnavailableServiceState(context, subId) ?: return
        synchronized(unavailableCarrierSlots) {
            if (unavailable) {
                unavailableCarrierSlots.add(slot)
            } else {
                unavailableCarrierSlots.remove(slot)
            }
        }
    }


    @SuppressLint("MissingPermission")
    private fun getUnavailableServiceState(context: Context?, subId: Int): Boolean? {
        val telephonyManager = getTelephonyManager(context, subId) ?: return null
        return runCatching {
            when (telephonyManager.serviceState?.state) {
                ServiceState.STATE_OUT_OF_SERVICE,
                ServiceState.STATE_EMERGENCY_ONLY -> true

                null -> null
                else -> false
            }
        }.getOrNull()
    }

    private fun getTelephonyManager(context: Context?, subId: Int): TelephonyManager? {
        if (context == null || !SubscriptionManager.isValidSubscriptionId(subId)) return null
        synchronized(telephonyManagersBySubId) {
            telephonyManagersBySubId[subId]?.let { return it }
            return context
                .getSystemService(TelephonyManager::class.java)
                ?.createForSubscriptionId(subId)
                ?.also { telephonyManagersBySubId[subId] = it }
        }
    }

    private fun isUnavailableMobileStateText(state: Any): Boolean {
        val stateText = readFieldValue(
            state,
            listOf(
                "contentDescription",
                "typeContentDescription",
                "networkName",
                "carrierName"
            )
        )?.toString().orEmpty()

        return stateText.isNotEmpty() && isUnavailableCarrierText(stateText)
    }

    private fun sanitizeCarrierTextCallbackInfo(info: Any, selectedSlots: Set<Int>, context: Context?) {
        val carrierList = readCarrierListField(info)
        if (carrierList != null) {
            sanitizeCarrierList(info, carrierList, selectedSlots, context)
            return
        }

        val carrierText = readFieldValue(info, listOf("carrierText", "carrierTextShort"))
            ?.toString()
            .orEmpty()
        if (carrierText.isEmpty()) return

        val unavailableSlots = mutableSetOf<Int>()
        val availableSlots = mutableSetOf<Int>()
        readIntArrayField(info, listOf("subscriptionIds", "subs", "subIds"))
            ?.forEach { subId ->
                val slot = SubscriptionManager.getSlotIndex(subId)
                if (slot !in selectedSlots) return@forEach
                when (getUnavailableServiceState(context, subId)) {
                    true -> unavailableSlots.add(slot)
                    false -> availableSlots.add(slot)
                    null -> Unit
                }
            }

        synchronized(unavailableCarrierSlots) {
            unavailableCarrierSlots.removeAll(availableSlots)
            if (unavailableSlots.isNotEmpty()) {
                unavailableCarrierSlots.addAll(unavailableSlots)
                setFieldValue(info, "carrierText", "")
                setFieldValue(info, "carrierTextShort", "")
            } else if (availableSlots.isEmpty() && isUnavailableCarrierText(carrierText)) {
                unavailableCarrierSlots.addAll(selectedSlots)
                setFieldValue(info, "carrierText", "")
                setFieldValue(info, "carrierTextShort", "")
            }
        }
    }

    private fun sanitizeCarrierList(
        info: Any,
        carrierList: CarrierListField,
        selectedSlots: Set<Int>,
        context: Context?
    ) {
        val subscriptionIds = readIntArrayField(info, listOf("subscriptionIds", "subs", "subIds"))
        val originalCarriers = carrierList.values
        val sanitizedCarriers = originalCarriers.toMutableList()
        val unavailableSelectedSlots = mutableSetOf<Int>()
        val availableSelectedSlots = mutableSetOf<Int>()

        originalCarriers.forEachIndexed { index, carrier ->
            val slot = getCarrierSlot(index, subscriptionIds) ?: return@forEachIndexed
            if (slot !in selectedSlots) return@forEachIndexed

            val serviceUnavailable = subscriptionIds
                ?.getOrNull(index)
                ?.let { getUnavailableServiceState(context, it) }
            val carrierText = carrier?.toString().orEmpty()
            when {
                serviceUnavailable == true -> {
                    unavailableSelectedSlots.add(slot)
                    sanitizedCarriers[index] = ""
                }

                serviceUnavailable == false -> {
                    availableSelectedSlots.add(slot)
                }

                isUnavailableCarrierText(carrierText) -> {
                    unavailableSelectedSlots.add(slot)
                    sanitizedCarriers[index] = ""
                }
            }
        }

        synchronized(unavailableCarrierSlots) {
            unavailableCarrierSlots.removeAll(availableSelectedSlots)
            unavailableCarrierSlots.addAll(unavailableSelectedSlots)
        }

        if (unavailableSelectedSlots.isEmpty()) return

        writeCarrierListField(carrierList, sanitizedCarriers)
        val carrierText = buildCarrierText(originalCarriers, sanitizedCarriers)
        setFieldValue(info, "carrierText", carrierText)
        setFieldValue(info, "carrierTextShort", carrierText)
    }

    private fun getCarrierSlot(index: Int, subscriptionIds: IntArray?): Int? {
        val subId = subscriptionIds?.getOrNull(index)
        if (subId != null) {
            val slot = SubscriptionManager.getSlotIndex(subId)
            if (slot >= 0) return slot
        }
        return index.takeIf { it == PHYSICAL_ESIM_ADAPTER_SIM_1 || it == PHYSICAL_ESIM_ADAPTER_SIM_2 }
    }

    @SuppressLint("DiscouragedApi")
    private fun updateUnavailableCarrierTexts(context: Context?) {
        if (unavailableCarrierTextsLoaded) return
        context ?: return
        val packages = listOf(context.packageName, "android")
        val labels = unavailableCarrierTextResourceNames.flatMap { name ->
            packages.mapNotNull { packageName ->
                val id = context.resources.getIdentifier(name, "string", packageName)
                if (id == 0) null else runCatching { context.getString(id) }.getOrNull()
            }
        }

        if (labels.isEmpty()) return

        synchronized(unavailableCarrierTexts) {
            unavailableCarrierTexts.addAll(labels.map(::normalizeCarrierText).filter(String::isNotBlank))
            unavailableCarrierTextsLoaded = true
        }
    }

    private fun isUnavailableCarrierText(text: String): Boolean {
        val normalized = normalizeCarrierText(text)
        if (normalized.isBlank()) return false

        return synchronized(unavailableCarrierTexts) {
            val unavailableLabels = unavailableCarrierTexts.ifEmpty { unavailableCarrierTextFallbacks }
            unavailableLabels.any { label ->
                label.isNotBlank() && (normalized == label || normalized.contains(label))
            }
        }
    }

    private fun normalizeCarrierText(text: String): String {
        val normalized = StringBuilder(text.length)
        var pendingSpace = false
        text.forEach { char ->
            when {
                isCarrierTextControlChar(char) -> Unit
                char.isWhitespace() -> {
                    if (normalized.isNotEmpty()) pendingSpace = true
                }

                else -> {
                    if (pendingSpace) {
                        normalized.append(' ')
                        pendingSpace = false
                    }
                    normalized.append(char.lowercaseChar())
                }
            }
        }
        return normalized.toString()
    }

    private fun isCarrierTextControlChar(char: Char): Boolean =
        when (char.code) {
            0x200e, 0x200f -> true
            in 0x202a..0x202e -> true
            in 0x2066..0x2069 -> true
            else -> false
        }

    private fun updatePhysicalEsimAdapterContext(context: Context?) {
        context ?: return
        physicalEsimAdapterContext = context.applicationContext ?: context
    }

    private fun buildCarrierText(
        originalCarriers: List<CharSequence?>,
        sanitizedCarriers: List<CharSequence?>
    ): String {
        val visibleCarriers = sanitizedCarriers
            .mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
        if (visibleCarriers.isEmpty()) return ""
        if (visibleCarriers.size == 1) return visibleCarriers.first()

        val originalText = originalCarriers
            .mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            .joinToString()
        val separator = detectCarrierSeparator(originalText, originalCarriers)
        return visibleCarriers.joinToString(separator)
    }

    private fun detectCarrierSeparator(
        carrierText: String,
        carriers: List<CharSequence?>
    ): String {
        val carrierNames = carriers.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
        if (carrierNames.size < 2) return ", "

        val first = carrierNames[0]
        val second = carrierNames[1]
        val firstIndex = carrierText.indexOf(first)
        if (firstIndex < 0) return ", "

        val separatorStart = firstIndex + first.length
        val secondIndex = carrierText.indexOf(second, separatorStart)
        if (secondIndex <= separatorStart) return ", "

        return carrierText.substring(separatorStart, secondIndex)
    }

    private data class CarrierListField(
        val field: Field,
        val holder: Any,
        val owner: Any,
        val values: List<CharSequence?>
    )

    private fun readCarrierListField(info: Any): CarrierListField? {
        val field = findField(
            info,
            listOf("listOfCarriers", "carrierTextList", "carrierTexts", "networkNames")
        ) ?: return null
        val value = runCatching { field.get(info) }.getOrNull() ?: return null
        val values = when (value) {
            is Array<*> -> value.map { it as? CharSequence }
            is List<*> -> value.map { it as? CharSequence }
            else -> return null
        }
        return CarrierListField(field, info, value, values)
    }

    @Suppress("UNCHECKED_CAST")
    private fun writeCarrierListField(
        carrierList: CarrierListField,
        values: List<CharSequence?>
    ) {
        runCatching {
            when (val owner = carrierList.owner) {
                is Array<*> -> values.forEachIndexed { index, value ->
                    java.lang.reflect.Array.set(owner, index, value)
                }

                is MutableList<*> -> (owner as MutableList<CharSequence?>).also { list ->
                    values.forEachIndexed { index, value -> list[index] = value }
                }
            }
        }.onFailure {
            runCatching { carrierList.field.set(carrierList.holder, values) }
        }
    }

    private fun readIntArrayField(info: Any, names: List<String>): IntArray? {
        val value = readFieldValue(info, names) ?: return null
        return when (value) {
            is IntArray -> value
            is Array<*> -> value.filterIsInstance<Int>().toIntArray()
            else -> null
        }
    }

    private fun readFieldValue(instance: Any, names: List<String>): Any? =
        findField(instance, names)?.let { field -> runCatching { field.get(instance) }.getOrNull() }

    private fun setFieldValue(instance: Any, name: String, value: Any?) {
        findField(instance, listOf(name))?.let { field ->
            runCatching { field.set(instance, value) }
        }
    }

    private fun findField(instance: Any, names: List<String>): Field? {
        names.forEach { name ->
            findFieldIfExists(instance.javaClass, name)?.let { return it }
        }
        return null
    }
}
