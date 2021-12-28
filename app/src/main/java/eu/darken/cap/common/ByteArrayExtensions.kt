package eu.darken.cap.common

import java.util.*

fun Byte.toHex(): String = String.format("%02X", this)
fun UByte.toHex(): String = this.toByte().toHex()

val Byte.upperNibble get() = (this.toInt() shr 4 and 0b1111).toByte()
val Byte.lowerNibble get() = (this.toInt() and 0b1111).toByte()
val UByte.upperNibble get() = (this.toInt() shr 4 and 0b1111).toUByte()
val UByte.lowerNibble get() = (this.toInt() and 0b1111).toUByte()

fun Byte.isBitSet(pos: Int): Boolean = BitSet.valueOf(arrayOf(this).toByteArray()).get(pos)
fun UByte.isBitSet(pos: Int): Boolean = this.toByte().isBitSet(pos)