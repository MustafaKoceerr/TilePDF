package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.feature.reader.R
import com.mustafakoceerr.tilepdf.model.PageTheme
import com.mustafakoceerr.tilepdf.model.ReaderPreferences
import com.mustafakoceerr.tilepdf.model.ReadingMode

/** Reader settings that apply to every document. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(
    preferences: ReaderPreferences,
    onPageThemeSelected: (PageTheme) -> Unit,
    onReadingModeSelected: (ReadingMode) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onTwoPageSpreadsChange: (Boolean) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChoiceRow(
                title = R.string.reader_layout,
                options = ReadingMode.entries,
                selected = preferences.readingMode,
                label = ReadingMode::labelRes,
                onSelect = onReadingModeSelected,
            )
            ThemeChoice(selected = preferences.pageTheme, onSelect = onPageThemeSelected)
            SwitchRow(R.string.reader_keep_screen_on, preferences.keepScreenOn, onKeepScreenOnChange)
            SwitchRow(R.string.reader_volume_keys, preferences.volumeKeysTurnPages, onVolumeKeysChange)
            // Spreads are a horizontal-layout thing.
            if (preferences.readingMode == ReadingMode.Horizontal) {
                SwitchRow(R.string.reader_two_page_spreads, preferences.twoPageSpreads, onTwoPageSpreadsChange)
            }
        }
    }
}

@Composable
private fun SwitchRow(@StringRes title: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        // The row handles the toggle, so the whole line is one target.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A titled row of mutually exclusive options. */
@Composable
private fun <T> ChoiceRow(
    @StringRes title: Int,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(stringResource(label(option))) },
                )
            }
        }
    }
}

/** Day themes first, then the dark ones, darkest last. */
private val ThemeOrder = listOf(
    PageTheme.Light, PageTheme.Sepia, PageTheme.Green, PageTheme.Dim, PageTheme.WarmNight, PageTheme.Night,
)

/** The page themes as chips, each with a dot of its paper colour; too many for one segmented row. */
@Composable
private fun ThemeChoice(selected: PageTheme, onSelect: (PageTheme) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.reader_theme), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeOrder.forEach { theme ->
                FilterChip(
                    selected = theme == selected,
                    onClick = { onSelect(theme) },
                    label = { Text(stringResource(theme.labelRes)) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(16.dp)
                                .background(theme.paperColor, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        )
                    },
                )
            }
        }
    }
}

@get:StringRes
private val ReadingMode.labelRes: Int
    get() = when (this) {
        ReadingMode.Vertical -> R.string.reader_layout_vertical
        ReadingMode.Horizontal -> R.string.reader_layout_horizontal
    }

@get:StringRes
private val PageTheme.labelRes: Int
    get() = when (this) {
        PageTheme.Light -> R.string.reader_theme_light
        PageTheme.Night -> R.string.reader_theme_night
        PageTheme.Sepia -> R.string.reader_theme_sepia
        PageTheme.Green -> R.string.reader_theme_green
        PageTheme.Dim -> R.string.reader_theme_dim
        PageTheme.WarmNight -> R.string.reader_theme_warm_night
    }
