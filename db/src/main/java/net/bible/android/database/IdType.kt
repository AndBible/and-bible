/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.database

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.lang.Long.decode
import java.lang.Long.toHexString
import java.security.SecureRandom

object UUIDSerializer : KSerializer<MyUUID> {
    override val descriptor = PrimitiveSerialDescriptor("MyUUID", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): MyUUID = MyUUID.fromString(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: MyUUID) = encoder.encodeString(value.toString())
}

object IdTypeSerializer : KSerializer<IdType> {
    override val descriptor = PrimitiveSerialDescriptor("IdType", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): IdType = IdType.fromString(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: IdType) = encoder.encodeString(value.toString())
}

private val randomGenerator = SecureRandom()

/**
 * Like UUID but not quite UUID. Just 128 bit random binary.
 * Reason: SQLite does not easily generate UUIDs.
 * Mostly copied from java.util.UUID class.
 */
class MyUUID(
    val mostSignificantBits: Long,
    val leastSignificantBits: Long,
): Comparable<MyUUID> {
    override fun toString(): String =
        digits(mostSignificantBits shr 32, 8) + "-" +
        digits(mostSignificantBits shr 16, 4) + "-" +
        digits(mostSignificantBits, 4) + "-" +
        digits(leastSignificantBits shr 48, 4) + "-" +
        digits(leastSignificantBits, 12)

    override fun hashCode(): Int {
        val hilo: Long = mostSignificantBits xor leastSignificantBits
        return (hilo shr 32).toInt() xor hilo.toInt()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MyUUID
        if (mostSignificantBits != other.mostSignificantBits) return false
        if (leastSignificantBits != other.leastSignificantBits) return false
        return true
    }

    fun toByteArray(): ByteArray {
        val l1 = mostSignificantBits
        val l2 = leastSignificantBits

        var l = l1
        val result = ByteArray(16)
        for (i in 8 - 1 downTo 0) {
            result[i] = (l and 0xFFL).toByte()
            l = l shr java.lang.Byte.SIZE
        }
        l = l2
        for (i in 16 - 1 downTo 8) {
            result[i] = (l and 0xFFL).toByte()
            l = l shr java.lang.Byte.SIZE
        }
        return result
    }

    override fun compareTo(other: MyUUID): Int = compareValuesBy(this, other, {mostSignificantBits}, {leastSignificantBits})

    companion object {
        fun randomUUID(): MyUUID = MyUUID(randomGenerator.nextLong(), randomGenerator.nextLong())
        fun fromString(name: String): MyUUID {
            val components = name.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            require(components.size == 5) { "Invalid UUID string: $name" }
            for (i in 0..4) components[i] = "0x" + components[i]
            var mostSigBits = decode(components[0]).toLong()
            mostSigBits = mostSigBits shl 16
            mostSigBits = mostSigBits or decode(components[1]).toLong()
            mostSigBits = mostSigBits shl 16
            mostSigBits = mostSigBits or decode(components[2]).toLong()
            var leastSigBits = decode(components[3]).toLong()
            leastSigBits = leastSigBits shl 48
            leastSigBits = leastSigBits or decode(components[4]).toLong()
            return MyUUID(mostSigBits, leastSigBits)
        }

        fun fromByteArray(value: ByteArray): MyUUID {
            if(value.size != 16) {
                throw RuntimeException("Blob size != 16 but ${value.size}")
            }
            var bits1 = 0L
            for (i in 0 until 8) {
                bits1 = bits1 shl java.lang.Byte.SIZE
                bits1 = bits1 or (value[i].toInt() and 0xFF).toLong()
            }
            var bits2 = 0L
            for (i in 8 until 16) {
                bits2 = bits2 shl java.lang.Byte.SIZE
                bits2 = bits2 or (value[i].toInt() and 0xFF).toLong()
            }
            return MyUUID(bits1, bits2)
        }
        fun digits(value: Long, digits: Int): String {
            val hi = 1L shl (digits * 4)
            return toHexString(hi or (value and (hi - 1))).substring(1)
        }
    }
}

@Serializable(with = IdTypeSerializer::class)
data class IdType(
    @Serializable(with = UUIDSerializer::class)
    private val myUUID: MyUUID? = MyUUID.randomUUID(),
): Comparable<IdType> {
    constructor(s: String?): this(if(s == null) null else try {MyUUID.fromString(s)} catch (e: IllegalArgumentException) {null})
    override fun compareTo(other: IdType): Int = compareValuesBy(this, other) { it.myUUID }
    override fun toString(): String = myUUID?.toString()?: ""
    val isEmpty get() = myUUID == null
    override fun hashCode(): Int = myUUID.hashCode()

    override fun equals(other: Any?): Boolean {
        if(other !is IdType) return false
        return this.myUUID == other.myUUID
    }
    fun toByteArray(): ByteArray? = myUUID?.toByteArray()
    companion object {
        fun empty() = IdType(null as String?)
        fun fromString(value: String) = if(value.isEmpty()) empty() else IdType(MyUUID.fromString(value))
        fun fromByteArray(value: ByteArray?): IdType? {
            if(value==null) return null
            if(value.size == 1) {
                return empty()
            }
            return IdType(MyUUID.fromByteArray(value))
        }
    }
}
