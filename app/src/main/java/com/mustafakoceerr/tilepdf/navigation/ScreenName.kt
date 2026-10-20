package com.mustafakoceerr.tilepdf.navigation

/**
 * The statistics name of a type-safe route, e.g. "reader" for
 * `…ReaderRoute/{uri}`. The arguments are dropped: they hold document URIs.
 */
internal fun screenNameOf(route: String?): String? = route
    ?.substringBefore('/')
    ?.substringBefore('?')
    ?.substringAfterLast('.')
    ?.removeSuffix("Route")
    ?.lowercase()
