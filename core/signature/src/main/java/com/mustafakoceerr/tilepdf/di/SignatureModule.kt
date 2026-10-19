package com.mustafakoceerr.tilepdf.di

import com.mustafakoceerr.tilepdf.core.signature.AndroidTrustAnchors
import com.mustafakoceerr.tilepdf.core.signature.SignatureVerifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SignatureModule {

    @Provides
    @Singleton
    fun providesSignatureVerifier(): SignatureVerifier {
        // Read once, when the first signature is checked.
        val anchors by lazy { AndroidTrustAnchors.load() }
        return SignatureVerifier { anchors }
    }
}
