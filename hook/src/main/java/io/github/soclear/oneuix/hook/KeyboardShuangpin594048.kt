package io.github.soclear.oneuix.hook

import android.util.Log
import android.util.SparseArray
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.hook.util.xlog
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Version-pinned adapter: never apply these obfuscated bindings to a different keyboard build. */
internal object KeyboardShuangpin594048 {
    private const val VERSION_CODE = 594048300L
    private const val XT9 = "com.samsung.android.honeyboard.predictionengine.core.xt9."

    context(xposedModule: XposedModule)
    fun install(versionCode: Long, classLoader: ClassLoader) {
        if (versionCode != VERSION_CODE) {
            xlog("Keyboard: XT9 input adapter skipped for unsupported version $versionCode", priority = Log.INFO)
            return
        }
        // Resolve and validate every binding before installing any input hook. Loading classes does
        // not initialize feature flags here: HoneyBoard's DI is not ready until Application.onCreate.
        val b = Bindings(classLoader)
        val reported = ConcurrentHashMap.newKeySet<String>()
        fun report(key: String, message: String) {
            if (reported.add(key)) xlog("Keyboard: $message", priority = Log.INFO)
        }
        fun shuangpin(owner: Any?, field: Field): Boolean = try {
            val enabled = b.isShuangpin.invoke(field.get(owner)) == true
            if (enabled) report("gates", "XT9 Shuangpin active; P1=${b.symbolGate.getBoolean(null)}, u1=${b.keyGate.getBoolean(null)}")
            enabled
        } catch (t: Throwable) {
            if (reported.add("state-error")) xlog("Keyboard: cannot read XT9 layout state; using original behavior", t)
            false
        }

        val handles = mutableListOf<XposedInterface.HookHandle>()
        try {
            handles += xposedModule.hook(b.keyboardId).intercept { chain ->
                val original = chain.proceed() as Int
                val language = chain.args[0] as Int
                val fixed = ShuangpinRules.keyboardId(
                    language, original,
                    language == 225 && shuangpin(chain.thisObject, b.idStore)
                )
                if (fixed != original) report("kdb", "XT9 Shuangpin keyboard database corrected: 0x${original.toString(16)} -> 0x${fixed.toString(16)}")
                fixed
            }
            handles += xposedModule.hook(b.validateSymbol).intercept { chain ->
                val original = chain.proceed()
                if (original == false && ShuangpinRules.isSymbol(chain.args[0] as Int) &&
                    shuangpin(chain.thisObject, b.wrapperStore)
                ) {
                    report("validation", "XT9 Shuangpin symbol validation enabled")
                    true
                } else original
            }
            handles += xposedModule.hook(b.composingSpell).intercept { chain ->
                val original = chain.proceed() as String
                if (!shuangpin(chain.thisObject, b.spellStore)) return@intercept original
                try {
                    if (b.symbolGate.getBoolean(null)) return@intercept original
                    // The original formatter DROPS unknown private-use symbols when P1 is false.
                    // Read the native spell buffer, rather than trying to repair its empty result.
                    val spell = b.spellBuffer.get(null)
                    val chars = b.spellSymbols.get(spell) as CharArray
                    val length = b.spellLength.getByte(spell).toInt().coerceIn(0, minOf(224, chars.size))
                    val raw = String(chars, 0, length)
                    if (!raw.any { ShuangpinRules.isSymbol(it.code) }) return@intercept original
                    val names = b.symbolNames.get(null) as SparseArray<*>
                    val fixed = ShuangpinRules.renderSymbols(raw, { names[it] as? String }) { char ->
                        val text = StringBuilder()
                        b.appendSymbol.invoke(null, text, charArrayOf(char), 0, 1)
                        text.toString()
                    }
                    report("spelling", "XT9 Shuangpin spelling conversion enabled")
                    fixed
                } catch (t: Throwable) {
                    if (reported.add("spelling-error")) xlog("Keyboard: XT9 spelling conversion failed", t)
                    original
                }
            }
            handles += xposedModule.hook(b.processElseKey).intercept { chain ->
                // Restore only u1's Shuangpin branch, NOT its phonepad behavior. Never temporarily
                // change global feature fields, which could race with other input threads/layouts.
                if (chain.args[3] == true || !shuangpin(chain.thisObject, b.databaseStore)) {
                    return@intercept chain.proceed()
                }
                val key = chain.args[0]
                val restored = try {
                    val label = b.label.get(key) as? CharSequence
                    val codes = b.codes.get(key) as IntArray
                    if (!ShuangpinRules.needsSymbolKey(true, false, label, codes) || b.keyGate.getBoolean(null)) {
                        null
                    } else {
                        b.keyConstructor.newInstance(
                            codes, 0, b.x.getInt(key), b.y.getInt(key), b.width.getInt(key), b.height.getInt(key),
                            chain.args[4], chain.args[2], chain.args[5], chain.args[6]
                        )
                    }
                } catch (t: Throwable) {
                    if (reported.add("key-error")) xlog("Keyboard: XT9 symbol key construction failed; using original behavior", t)
                    null
                }
                if (restored == null) return@intercept chain.proceed()
                @Suppress("UNCHECKED_CAST")
                (chain.args[1] as MutableList<Any>).add(restored)
                report("keys", "XT9 Shuangpin symbol key construction enabled")
                null
            }
        } catch (t: Throwable) {
            handles.asReversed().forEach { runCatching { it.unhook() } }
            throw t
        }
        // These callers may already contain AOT-inlined copies of the patched methods.
        b.callers.forEach { method ->
            runCatching {
                if (!xposedModule.deoptimize(method)) {
                    xlog("Keyboard: deoptimization declined for XT9 caller ${method.name}")
                }
            }.onFailure { xlog("Keyboard: cannot deoptimize XT9 caller ${method.name}", it) }
        }
        xlog("Keyboard: XT9 Shuangpin input adapter installed for 5.9.40.48", priority = Log.INFO)

        // Status only: no key symbols, composing strings, candidates or user text are logged.
        b.diagnostics.forEach { method ->
            runCatching {
                xposedModule.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val argument = (chain.args[0] as Number).toInt()
                    val relevant = when (method.name) {
                        "ET9CPSetInputMode" -> argument == 3
                        "ET9CPLdbInit" -> argument == 225
                        else -> argument == (225 or 0x0f00)
                    }
                    if (relevant) report("${method.name}:$result", "${method.name}($argument) returned $result")
                    result
                }
            }.onFailure { xlog("Keyboard: cannot install XT9 status diagnostic ${method.name}", it) }
        }
    }

    private class Bindings(loader: ClassLoader) {
        private val intType = Int::class.javaPrimitiveType!!
        private val boolType = Boolean::class.javaPrimitiveType!!
        private val store = loader.loadClass("xj.b")
        private val engine = loader.loadClass("${XT9}i")
        private val wrapper = loader.loadClass("${XT9}Xt9Wrapper")
        private val getter = loader.loadClass("${XT9}g")
        private val database = loader.loadClass("${XT9}XT9KeyboardDatabase")
        private val keyData = loader.loadClass("X6.c")
        private val feature = loader.loadClass("d9.a")
        private fun Class<*>.field(name: String, type: Class<*>): Field = getDeclaredField(name).apply {
            check(this.type == type) { "Unexpected XT9 field type: $this" }
            isAccessible = true
        }
        private fun Class<*>.method(name: String, result: Class<*>, vararg params: Class<*>): Method =
            getDeclaredMethod(name, *params).apply {
                check(returnType == result) { "Unexpected XT9 method type: $this" }
                isAccessible = true
            }

        val isShuangpin = store.method("y", boolType)
        val idStore = engine.field("a", store)
        val wrapperStore = wrapper.field("i", store)
        val spellStore = getter.field("a", store)
        val databaseStore = database.field("mStore", store)
        val symbolGate = feature.field("P1", boolType)
        val keyGate = feature.field("u1", boolType)
        val symbolNames = loader.loadClass("${XT9}j").field("b", SparseArray::class.java)
        private val spellData = loader.loadClass("${XT9}datatype.S_ET9CPInfo\$S_ET9CPSpell")
        val spellBuffer = loader.loadClass("${XT9}o").field("D", spellData)
        val spellLength = spellData.field("bLen", Byte::class.javaPrimitiveType!!)
        val spellSymbols = spellData.field("pSymbs", CharArray::class.java)
        val appendSymbol = loader.loadClass("${XT9}datatype.a").method(
            "a", Void.TYPE, StringBuilder::class.java, CharArray::class.java, intType, intType
        )
        val label = keyData.field("a", CharSequence::class.java)
        val codes = keyData.field("c", IntArray::class.java)
        val x = keyData.field("d", intType)
        val y = keyData.field("e", intType)
        val width = keyData.field("f", intType)
        val height = keyData.field("g", intType)
        val keyboardId = engine.method("a", intType, intType)
        val validateSymbol = wrapper.method("r1", boolType, intType)
        val composingSpell = getter.method("a", String::class.java)
        val processElseKey = database.method(
            "processElseKey", Void.TYPE, keyData, List::class.java, StringBuilder::class.java,
            boolType, intType, boolType, String::class.java, intType
        )
        val keyConstructor = loader.loadClass("${XT9}XT9KeyboardDatabase\$XT9Key").getDeclaredConstructor(
            IntArray::class.java, intType, intType, intType, intType, intType, intType,
            StringBuilder::class.java, boolType, String::class.java
        ).apply { isAccessible = true }
        val callers = wrapper.declaredMethods.filter { it.name in setOf("o0", "S0") } +
            database.getDeclaredMethod("makeKeys", loader.loadClass("X6.b")) +
            loader.loadClass("${XT9}p").declaredMethods.filter { it.name == "a" } +
            loader.loadClass("vj.e").method("r1", boolType, intType)
        val diagnostics = loader.loadClass("${XT9}Xt9core").declaredMethods.filter {
            it.name in setOf("ET9CPSetInputMode", "ET9CPLdbInit", "ET9KDB_SetKdbNum")
        }
    }
}
