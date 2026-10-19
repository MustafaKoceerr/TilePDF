plugins {
    alias(libs.plugins.tilepdf.android.library)
    alias(libs.plugins.tilepdf.hilt)
}

dependencies {
    api(projects.core.pdf)
    // CMS and ASN.1 parsing only: signatures and chains are checked with the platform's JCA.
    implementation(libs.bouncycastle.pkix)
    implementation(libs.bouncycastle.prov)

    testImplementation(projects.core.pdfwriter)
}
