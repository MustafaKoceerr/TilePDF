package com.mustafakoceerr.tilepdf

/** The environment variables that hold the Play upload key; nothing is ever stored in the repo. */
val RELEASE_SIGNING_VARIABLES = listOf(
    "TILEPDF_UPLOAD_KEYSTORE",
    "TILEPDF_UPLOAD_KEYSTORE_PASSWORD",
    "TILEPDF_UPLOAD_KEY_ALIAS",
    "TILEPDF_UPLOAD_KEY_PASSWORD",
)

/** How a release build is signed, decided from the environment. */
sealed interface ReleaseSigning {

    data class Configured(
        val storeFile: String,
        val storePassword: String,
        val keyAlias: String,
        val keyPassword: String,
    ) : ReleaseSigning {
        /** Build logs may print this; the secrets stay out. */
        override fun toString() = "Configured(storeFile=$storeFile)"
    }

    /** No variable set: a local release build, unsigned. */
    data object Unsigned : ReleaseSigning

    /** Some set, some not: almost certainly a mistake, e.g. a CI secret that didn't come through. */
    data class Incomplete(val missing: List<String>) : ReleaseSigning {
        /** Names only; the values that are set never appear. */
        val message: String
            get() = "Release signing is half configured; also set ${missing.joinToString()} " +
                "(or none of ${RELEASE_SIGNING_VARIABLES.joinToString()})."
    }
}

/** Blank counts as missing, so an empty CI secret isn't taken for a password. */
fun releaseSigning(env: (String) -> String?): ReleaseSigning {
    val values = RELEASE_SIGNING_VARIABLES.associateWith { name -> env(name)?.takeIf(String::isNotBlank) }
    val missing = values.filterValues { it == null }.keys.toList()
    return when (missing.size) {
        RELEASE_SIGNING_VARIABLES.size -> ReleaseSigning.Unsigned
        0 -> {
            val (storeFile, storePassword, keyAlias, keyPassword) = RELEASE_SIGNING_VARIABLES.map { values.getValue(it)!! }
            ReleaseSigning.Configured(storeFile, storePassword, keyAlias, keyPassword)
        }
        else -> ReleaseSigning.Incomplete(missing)
    }
}
