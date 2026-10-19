package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.core.designsystem.TilePdfIcons
import com.mustafakoceerr.tilepdf.feature.reader.R
import com.mustafakoceerr.tilepdf.feature.reader.SignatureSummary
import com.mustafakoceerr.tilepdf.feature.reader.signatureSummary
import com.mustafakoceerr.tilepdf.model.SignatureCheck
import com.mustafakoceerr.tilepdf.model.SignatureIntegrity
import java.text.DateFormat
import java.util.Date

/** The icon and its colour for a summary: green, amber or red. */
@Composable
private fun SignatureSummary.look(): Pair<Int, Color> = when (this) {
    SignatureSummary.Valid -> TilePdfIcons.Verified to Color(0xFF2E7D32)
    SignatureSummary.ChangedAfterSigning, SignatureSummary.Untrusted, SignatureSummary.Unchecked ->
        TilePdfIcons.Warning to Color(0xFFB26A00)
    SignatureSummary.Broken -> TilePdfIcons.ErrorOutline to MaterialTheme.colorScheme.error
}

private val SignatureSummary.label: Int
    get() = when (this) {
        SignatureSummary.Valid -> R.string.signatures_chip_valid
        SignatureSummary.ChangedAfterSigning -> R.string.signatures_chip_changed
        SignatureSummary.Untrusted -> R.string.signatures_chip_untrusted
        SignatureSummary.Unchecked -> R.string.signatures_chip_unchecked
        SignatureSummary.Broken -> R.string.signatures_chip_broken
    }

/** Tells at a glance what the document's signatures say; opens the list. */
@Composable
internal fun SignaturesChip(summary: SignatureSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (icon, tint) = summary.look()
    ElevatedAssistChip(
        onClick = onClick,
        label = { Text(stringResource(summary.label)) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, tint = tint) },
        modifier = modifier.padding(bottom = 16.dp),
    )
}

/** Every signature of the document, each with what was checked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignaturesSheet(checks: List<SignatureCheck>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.navigationBarsPadding()) {
            item {
                Text(
                    stringResource(R.string.signatures_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            items(checks) { check ->
                SignatureRow(check)
                HorizontalDivider()
            }
            item {
                Text(
                    stringResource(R.string.signatures_revocation_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun SignatureRow(check: SignatureCheck) {
    val summary = signatureSummary(listOf(check)) ?: return
    val (icon, tint) = summary.look()
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(painterResource(icon), contentDescription = stringResource(summary.label), tint = tint, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.signature_signed_by, check.signer.ifBlank { "?" }),
                style = MaterialTheme.typography.titleMedium,
            )
            check.signedAt?.let {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)), style = MaterialTheme.typography.bodyMedium)
            }
            if (check.reason.isNotBlank()) Text(stringResource(R.string.signature_reason, check.reason), style = MaterialTheme.typography.bodyMedium)
            if (check.issuer.isNotBlank()) {
                Text(
                    stringResource(R.string.signature_issued_by, check.issuer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(
                    when (check.integrity) {
                        SignatureIntegrity.Intact -> R.string.signature_intact
                        SignatureIntegrity.Broken -> R.string.signature_broken
                        SignatureIntegrity.Unsupported -> R.string.signature_unsupported
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (check.integrity == SignatureIntegrity.Intact) {
                Text(
                    stringResource(if (check.trusted) R.string.signature_trusted else R.string.signature_untrusted),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (check.changedAfterSigning) Text(stringResource(R.string.signature_changed_after), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
