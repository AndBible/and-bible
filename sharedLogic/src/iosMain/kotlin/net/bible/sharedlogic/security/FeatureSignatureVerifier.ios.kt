@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedlogic.security

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyGetBlockSize
import platform.Security.SecKeyVerifySignature
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA1

internal actual fun verifyFeatureSignature(publicKeyDer: ByteArray, content: ByteArray, signature: ByteArray): Boolean {
    // JCA uses SubjectPublicKeyInfo; Security imports the inner PKCS#1 RSAPublicKey instead.
    val pkcs1 = rsaPkcs1(publicKeyDer) ?: return false
    val attributes = CFDictionaryCreateMutable(
        kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
    ) ?: return false
    val keyData = pkcs1.cfData() ?: run { CFRelease(attributes); return false }
    val key = try {
        CFDictionarySetValue(attributes, kSecAttrKeyType, kSecAttrKeyTypeRSA)
        CFDictionarySetValue(attributes, kSecAttrKeyClass, kSecAttrKeyClassPublic)
        SecKeyCreateWithData(keyData, attributes, null)
    } finally {
        CFRelease(keyData)
        CFRelease(attributes)
    } ?: return false
    try {
        // The .sign format is binary, and Android ignores bytes beyond the RSA modulus size.
        val block = SecKeyGetBlockSize(key).toInt()
        if (signature.size < block) return false
        val message = content.cfData() ?: return false
        try {
            val signatureData = signature.copyOf(block).cfData() ?: return false
            try {
                return SecKeyVerifySignature(key, kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA1, message, signatureData, null)
            } finally {
                CFRelease(signatureData)
            }
        } finally {
            CFRelease(message)
        }
    } finally {
        CFRelease(key)
    }
}

private fun ByteArray.cfData(): CFDataRef? = usePinned {
    CFDataCreate(kCFAllocatorDefault, if (isEmpty()) null else it.addressOf(0).reinterpret(), size.toLong())
}

/** Extracts an RSA SPKI's BIT STRING without relying on a key-size-specific DER header length. */
private fun rsaPkcs1(spki: ByteArray): ByteArray? = runCatching {
    val outer = DerReader(spki)
    val sequence = DerReader(outer.value(0x30))
    require(outer.atEnd)
    val algorithm = DerReader(sequence.value(0x30))
    require(algorithm.value(0x06).contentEquals(byteArrayOf(0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 1, 1, 1)))
    if (!algorithm.atEnd) require(algorithm.value(0x05).isEmpty()) // rsaEncryption's optional NULL parameters
    require(algorithm.atEnd)
    val bits = sequence.value(0x03)
    require(sequence.atEnd && bits.size > 1 && bits[0] == 0.toByte()) // no unused bits
    bits.copyOfRange(1, bits.size)
}.getOrNull()

private class DerReader(private val bytes: ByteArray) {
    private var offset = 0
    val atEnd: Boolean get() = offset == bytes.size

    fun value(tag: Int): ByteArray {
        require(offset < bytes.size && bytes[offset++].toInt() and 0xff == tag)
        require(offset < bytes.size)
        val first = bytes[offset++].toInt() and 0xff
        var length = first
        if (first and 0x80 != 0) {
            val count = first and 0x7f
            require(count in 1..3 && count <= bytes.size - offset)
            length = 0
            repeat(count) { length = (length shl 8) or (bytes[offset++].toInt() and 0xff) }
        }
        require(length <= bytes.size - offset)
        return bytes.copyOfRange(offset, offset + length).also { offset += length }
    }
}
