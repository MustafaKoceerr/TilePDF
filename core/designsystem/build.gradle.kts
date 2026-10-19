plugins {
    alias(libs.plugins.tilepdf.android.library.compose)
}

dependencies {
    // TilePDFTheme takes the chosen ThemeSettings; callers see the type too.
    api(libs.theme.manager.compose)
    // TilePDFTheme takes the chosen AppPalette.
    api(projects.core.model)
}
