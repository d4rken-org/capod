package eu.darken.capod.common.bluetooth.l2cap

import android.util.Log
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method

/**
 * Bypasses Android's hidden API restrictions by calling [dalvik.system.VMRuntime.setHiddenApiExemptions].
 *
 * Technique: Uses [sun.misc.Unsafe] to iterate ART's internal method array and swap a stub method's
 * artMethod pointer to execute hidden methods. Mirror classes in [ArtMirror] provide field offsets
 * without triggering hidden API checks.
 *
 * Based on LSPosed/AndroidHiddenApiBypass (Apache 2.0)
 * https://github.com/LSPosed/AndroidHiddenApiBypass
 *
 * Copyright 2021 LSPosed
 * Licensed under the Apache License, Version 2.0
 */
object HiddenApiBypass {

    private const val TAG = "HiddenApiBypass"

    private val exemptedPrefixes = mutableSetOf<String>()

    @androidx.annotation.Keep
    private object InvokeStub {
        @JvmStatic
        fun invoke(vararg args: Any?): Any? {
            throw IllegalStateException("Stub — artMethod not swapped")
        }
    }

    @Synchronized
    fun setExemptions(vararg prefixes: String) {
        // Merge with existing prefixes (VMRuntime.setHiddenApiExemptions replaces, not merges)
        val newPrefixes = prefixes.filter { it !in exemptedPrefixes }
        if (newPrefixes.isEmpty()) return

        exemptedPrefixes.addAll(newPrefixes)
        val allPrefixes = exemptedPrefixes.toTypedArray()

        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredMethod("getUnsafe").invoke(null)
        val unsafeClass = unsafe::class.java
        val objectFieldOffset = unsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field::class.java)
        val getLong = unsafeClass.getMethod("getLong", Any::class.java, Long::class.javaPrimitiveType)
        val putLong = unsafeClass.getMethod("putLong", Any::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
        val getInt = unsafeClass.getMethod("getInt", Long::class.javaPrimitiveType)

        // --- Offset calculation from mirror classes ---
        val artMethodOff = objectFieldOffset.invoke(unsafe,
            ArtMirror.Executable::class.java.getDeclaredField("artMethod")) as Long
        val artFieldOrMethodOff = objectFieldOffset.invoke(unsafe,
            ArtMirror.MethodHandle::class.java.getDeclaredField("artFieldOrMethod")) as Long
        val methodsOff = objectFieldOffset.invoke(unsafe,
            ArtMirror.Class::class.java.getDeclaredField("methods")) as Long

        // --- Sanity check: verify artMethod offset matches the real accessible field ---
        val realArtMethodOff = objectFieldOffset.invoke(unsafe,
            java.lang.reflect.Executable::class.java.getDeclaredField("artMethod")) as Long
        check(artMethodOff == realArtMethodOff) {
            "ArtMirror.Executable.artMethod offset ($artMethodOff) != real offset ($realArtMethodOff). " +
                    "ART field layout has changed — mirror classes need updating."
        }

        // --- Calculate artMethodSize and bias from NeverCall ---
        val mA = NeverCall::class.java.getDeclaredMethod("a").apply { isAccessible = true }
        val mB = NeverCall::class.java.getDeclaredMethod("b").apply { isAccessible = true }
        val mhA = MethodHandles.lookup().unreflect(mA)
        val mhB = MethodHandles.lookup().unreflect(mB)
        val aAddr = getLong.invoke(unsafe, mhA, artFieldOrMethodOff) as Long
        val bAddr = getLong.invoke(unsafe, mhB, artFieldOrMethodOff) as Long
        val artMethodSize = bAddr - aAddr

        check(artMethodSize in 16..256) {
            "artMethodSize=$artMethodSize is outside expected range [16, 256]. ART internals may have changed."
        }

        val ncMethods = getLong.invoke(unsafe, NeverCall::class.java, methodsOff) as Long
        val artMethodBias = aAddr - ncMethods - artMethodSize

        // --- Iterate VMRuntime methods ---
        val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
        val vmMethods = getLong.invoke(unsafe, vmRuntimeClass, methodsOff) as Long
        val numMethods = getInt.invoke(unsafe, vmMethods) as Int

        check(numMethods in 1..10000) {
            "VMRuntime numMethods=$numMethods is outside expected range. Pointer may be invalid."
        }

        val stubMethod: Method = InvokeStub::class.java.getDeclaredMethod("invoke", Array<Any?>::class.java)
        stubMethod.isAccessible = true
        val originalArtMethod = getLong.invoke(unsafe, stubMethod, artMethodOff) as Long

        var runtime: Any? = null
        var exemptionsSet = false

        try {
            for (i in 0 until numMethods) {
                val methodPtr = vmMethods + i * artMethodSize + artMethodBias
                putLong.invoke(unsafe, stubMethod, artMethodOff, methodPtr)

                val name = stubMethod.name
                val params = stubMethod.parameterTypes

                if (name == "getRuntime" && params.isEmpty() && runtime == null) {
                    runtime = stubMethod.invoke(null)
                }

                if (name == "setHiddenApiExemptions" && params.size == 1 && params[0] == Array<String>::class.java) {
                    if (runtime == null) {
                        throw IllegalStateException("Found setHiddenApiExemptions before getRuntime")
                    }
                    stubMethod.invoke(runtime, allPrefixes as Any)
                    exemptionsSet = true
                    Log.d(TAG, "setHiddenApiExemptions OK: ${allPrefixes.contentToString()}")
                }

                if (runtime != null && exemptionsSet) break
            }
        } finally {
            // Always restore the stub's original artMethod pointer
            putLong.invoke(unsafe, stubMethod, artMethodOff, originalArtMethod)
        }

        if (runtime == null) throw RuntimeException("VMRuntime.getRuntime() not found in method array")
        if (!exemptionsSet) throw RuntimeException("VMRuntime.setHiddenApiExemptions() not found in method array")
    }
}
