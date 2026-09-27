package com.mustafakoceerr.tilepdf.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

/** [uri] is a SAF content URI string with a persisted read permission. */
@Serializable
data class ReaderRoute(val uri: String)
