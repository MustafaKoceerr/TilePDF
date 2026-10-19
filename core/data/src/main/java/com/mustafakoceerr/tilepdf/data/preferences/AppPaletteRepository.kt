package com.mustafakoceerr.tilepdf.data.preferences

import com.mustafakoceerr.tilepdf.model.AppPalette
import kotlinx.coroutines.flow.Flow

/** The app's colour palette; light, dark and Material You stay with the theme manager. */
interface AppPaletteRepository {
    val palette: Flow<AppPalette>
    suspend fun setPalette(palette: AppPalette)
}
