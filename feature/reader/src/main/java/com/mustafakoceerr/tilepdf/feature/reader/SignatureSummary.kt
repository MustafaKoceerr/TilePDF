package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.model.SignatureCheck
import com.mustafakoceerr.tilepdf.model.SignatureIntegrity

/** What a document's signatures add up to, from best to worst. */
enum class SignatureSummary { Valid, ChangedAfterSigning, Untrusted, Unchecked, Broken }

/** The worst of [checks]; null for a document without signatures. */
internal fun signatureSummary(checks: List<SignatureCheck>): SignatureSummary? =
    checks.maxOfOrNull { check ->
        when {
            check.integrity == SignatureIntegrity.Broken -> SignatureSummary.Broken
            check.integrity == SignatureIntegrity.Unsupported -> SignatureSummary.Unchecked
            !check.trusted -> SignatureSummary.Untrusted
            check.changedAfterSigning -> SignatureSummary.ChangedAfterSigning
            else -> SignatureSummary.Valid
        }
    }
