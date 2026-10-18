package com.mustafakoceerr.tilepdf.model

/** Whether a signature's signed bytes are as they were signed. */
enum class SignatureIntegrity {
    /** The signature matches the bytes it covers. */
    Intact,

    /** The bytes or the signature changed, or it can't be read. */
    Broken,

    /** A kind of signature TilePDF can't check. */
    Unsupported,
}

/**
 * What a digital signature in a document says.
 *
 * @param signer the signing certificate's common name, or its whole name.
 * @param signedAt epoch milliseconds, from the signature or the signature dictionary.
 * @param trusted whether the signing certificate chains to a trusted root at the signing time.
 * @param changedAfterSigning whether the file goes on past what was signed:
 *   it was changed after signing, as when annotations were saved into it.
 */
data class SignatureCheck(
    val signer: String,
    val issuer: String,
    val signedAt: Long?,
    val reason: String,
    val integrity: SignatureIntegrity,
    val trusted: Boolean,
    val changedAfterSigning: Boolean,
)
