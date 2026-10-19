package com.mustafakoceerr.tilepdf.feature.reader.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.security.KeyChain
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.core.designsystem.TilePdfIcons
import com.mustafakoceerr.tilepdf.feature.reader.R

private const val PKCS12_MIME_TYPE = "application/x-pkcs12"

/**
 * Picks the certificate to sign with from Android's store, through the
 * system's own dialog, or installs one there from a .p12/.pfx file first.
 * TilePDF never sees the file's password: the system's installer asks.
 */
@Composable
internal fun CertificateChoiceDialog(onChosen: (alias: String?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val install = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) installCertificate(context, uri)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(TilePdfIcons.Verified), contentDescription = null) },
        title = { Text(stringResource(R.string.certificate_title)) },
        text = { Text(stringResource(R.string.certificate_explanation)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(
                    onClick = {
                        val activity = context.findActivity() ?: return@TextButton onDismiss()
                        // Answers on a binder thread; the view model only updates state.
                        KeyChain.choosePrivateKeyAlias(activity, { alias -> onChosen(alias) }, null, null, null, null)
                    },
                ) { Text(stringResource(R.string.certificate_choose)) }
                TextButton(onClick = { install.launch(arrayOf(PKCS12_MIME_TYPE, "application/octet-stream")) }) {
                    Text(stringResource(R.string.certificate_install))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Hands a .p12/.pfx file to the system's installer, which asks for its password and keeps it. */
private fun installCertificate(context: Context, uri: Uri) {
    val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
    if (bytes == null) {
        Toast.makeText(context, R.string.certificate_install_failed, Toast.LENGTH_LONG).show()
        return
    }
    try {
        context.startActivity(KeyChain.createInstallIntent().putExtra(KeyChain.EXTRA_PKCS12, bytes))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.certificate_install_failed, Toast.LENGTH_LONG).show()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Shown while a signature is being made. */
@Composable
internal fun SigningProgress(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        modifier = modifier.padding(16.dp).widthIn(max = 480.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.inverseOnSurface, strokeWidth = 2.dp)
            Text(
                stringResource(R.string.certificate_signing),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}
