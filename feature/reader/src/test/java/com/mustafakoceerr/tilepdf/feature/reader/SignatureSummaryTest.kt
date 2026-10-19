package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.model.SignatureCheck
import com.mustafakoceerr.tilepdf.model.SignatureIntegrity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignatureSummaryTest {

    private fun check(
        integrity: SignatureIntegrity = SignatureIntegrity.Intact,
        trusted: Boolean = true,
        changed: Boolean = false,
    ) = SignatureCheck("Ada", "Root", null, "", integrity, trusted, changed)

    @Test
    fun noSignaturesNoSummary() {
        assertNull(signatureSummary(emptyList()))
    }

    @Test
    fun intactTrustedAndUnchangedIsValid() {
        assertEquals(SignatureSummary.Valid, signatureSummary(listOf(check(), check())))
    }

    @Test
    fun theWorstSignatureDecides() {
        assertEquals(SignatureSummary.ChangedAfterSigning, signatureSummary(listOf(check(), check(changed = true))))
        assertEquals(SignatureSummary.Untrusted, signatureSummary(listOf(check(changed = true), check(trusted = false))))
        assertEquals(SignatureSummary.Unchecked, signatureSummary(listOf(check(trusted = false), check(SignatureIntegrity.Unsupported, trusted = false))))
        assertEquals(SignatureSummary.Broken, signatureSummary(listOf(check(SignatureIntegrity.Unsupported), check(SignatureIntegrity.Broken))))
    }
}
