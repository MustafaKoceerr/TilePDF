package com.mustafakoceerr.tilepdf.core.pdf

/** A signature as the C++ code reads it; built through JNI. */
internal class NativeSignature(
    @JvmField val contents: ByteArray,
    @JvmField val byteRange: IntArray,
    @JvmField val subFilter: String,
    @JvmField val reason: String,
    @JvmField val time: String,
) {
    fun toPdfSignature() = PdfSignature(contents, byteRange.map { it.toLong() }, subFilter, reason, pdfDate(time))
}
