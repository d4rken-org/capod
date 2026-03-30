@file:Suppress("unused")

package eu.darken.capod.common.bluetooth.l2cap

import androidx.annotation.Keep
import java.lang.invoke.MethodType

/**
 * Mirror classes matching ART's internal field layout for offset calculation.
 * [sun.misc.Unsafe.objectFieldOffset] on these classes gives the same offsets as the real
 * hidden fields, because the field types and order are identical.
 *
 * Based on LSPosed/AndroidHiddenApiBypass (Apache 2.0)
 * https://github.com/LSPosed/AndroidHiddenApiBypass
 */
@Keep
object ArtMirror {

    @Keep
    open class AccessibleObject {
        private val override: Boolean = false
    }

    @Keep
    class Executable : AccessibleObject() {
        private val declaringClass: Any? = null
        private val declaringClassOfOverriddenMethod: Any? = null
        private val parameters: Array<Any>? = null
        @JvmField val artMethod: Long = 0
        private val accessFlags: Int = 0
    }

    @Keep
    class MethodHandle {
        private val type: MethodType? = null
        private val nominalType: MethodType? = null
        private val cachedSpreadInvoker: MethodHandle? = null
        private val handleKind: Int = 0
        @JvmField val artFieldOrMethod: Long = 0
    }

    @Keep
    class Class {
        private val classLoader: ClassLoader? = null
        private val componentType: kotlin.Any? = null
        private val dexCache: kotlin.Any? = null
        private val extData: kotlin.Any? = null
        private val ifTable: Array<kotlin.Any>? = null
        private val name: String? = null
        private val superClass: kotlin.Any? = null
        private val vtable: kotlin.Any? = null
        @JvmField val iFields: Long = 0
        @JvmField val methods: Long = 0
        @JvmField val sFields: Long = 0
        private val accessFlags: Int = 0
        private val classFlags: Int = 0
        private val classSize: Int = 0
        private val clinitThreadId: Int = 0
        private val dexClassDefIndex: Int = 0
        @Volatile private var dexTypeIndex: Int = 0
        private val numReferenceInstanceFields: Int = 0
        private val numReferenceStaticFields: Int = 0
        private val objectSize: Int = 0
        private val objectSizeAllocFastPath: Int = 0
        private val primitiveType: Int = 0
        private val referenceInstanceOffsets: Int = 0
        private val status: Int = 0
        private val copiedMethodsOffset: Short = 0
        private val virtualMethodsOffset: Short = 0
    }
}
