package com.mustafakoceerr.tilepdf.data.annotation

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passwords of the documents opened in this process, so a background save
 * can reopen them. Kept in memory only: never written to disk or logged.
 */
@Singleton
class SessionPasswords @Inject constructor() {

    private val passwords = ConcurrentHashMap<String, String>()

    fun remember(uri: String, password: String) {
        passwords[uri] = password
    }

    fun get(uri: String): String? = passwords[uri]

    /** The document at [from] now lives at [to], e.g. after saving a copy. */
    fun moved(from: String, to: String) {
        passwords.remove(from)?.let { passwords[to] = it }
    }
}
