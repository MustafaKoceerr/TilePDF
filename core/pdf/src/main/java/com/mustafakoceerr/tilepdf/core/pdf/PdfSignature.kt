package com.mustafakoceerr.tilepdf.core.pdf

/**
 * A digital signature as the file holds it.
 *
 * @param contents the signature itself, usually a CMS SignedData, perhaps zero padded.
 * @param byteRange the signed parts of the file: start and length pairs.
 * @param signedAt epoch milliseconds of the signature dictionary's /M, if any.
 */
class PdfSignature(
    val contents: ByteArray,
    val byteRange: List<Long>,
    val subFilter: String,
    val reason: String,
    val signedAt: Long?,
)
