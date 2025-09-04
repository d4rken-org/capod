package eu.darken.capod.common

import java.util.BitSet

fun Byte.toHex(): String = String.format("%02X", this)
fun UByte.toHex(): String = this.toByte().toHex()

val Byte.upperNibble get() = (this.toInt() shr 4 and 0b1111).toShort()
val Byte.lowerNibble get() = (this.toInt() and 0b1111).toShort()
val UByte.upperNibble get() = (this.toInt() shr 4 and 0b1111).toUShort()
val UByte.lowerNibble get() = (this.toInt() and 0b1111).toUShort()

fun Byte.isBitSet(pos: Int): Boolean = BitSet.valueOf(arrayOf(this).toByteArray()).get(pos)
fun UByte.isBitSet(pos: Int): Boolean = this.toByte().isBitSet(pos)

fun Short.isBitSet(pos: Int): Boolean = this.toByte().isBitSet(pos)
fun UShort.isBitSet(pos: Int): Boolean = this.toShort().isBitSet(pos)

fun UShort.toBinaryString(): String = Integer.toBinaryString(this.toInt()).padStart(4, '0')
fun UByte.toBinaryString(): String = Integer.toBinaryString(this.toInt()).padStart(8, '0')


fun ByteArray.toHex(separator: String = "-"): String = joinToString(separator) { "%02X".format(it) }
fun String.fromHex(): ByteArray = this
    .replace(" ", "")
    .replace("-", "")
    .also { require(it.length % 2 == 0) { "Not a HEX string, length: ${it.length}" } }
    .chunked(2).map { it.toInt(16).toByte() }
    .toByteArray()
