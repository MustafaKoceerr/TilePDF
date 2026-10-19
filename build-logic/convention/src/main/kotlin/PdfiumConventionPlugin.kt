import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.mustafakoceerr.tilepdf.CopyPdfiumLicensesTask
import com.mustafakoceerr.tilepdf.DownloadPdfiumTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

/**
 * Links TilePDF's own PDFium build into a module's CMake build: PDFium with
 * V8, XFA and TilePDF's patches, built from the `tilepdf` branch of
 * MustafaKoceerr/pdfium-binaries (a fork of bblanchon/pdfium-binaries). To
 * update PDFium, rebuild there, then change [RELEASE] and every hash in
 * [ARCHIVES].
 */
class PdfiumConventionPlugin : Plugin<Project> {

    private companion object {
        const val RELEASES = "https://github.com/MustafaKoceerr/pdfium-binaries/releases/download"
        const val RELEASE = "tilepdf-8076-1"

        /** Android ABI to the release archive and its SHA-256. */
        val ARCHIVES = mapOf(
            "arm64-v8a" to ("pdfium-v8-android-arm64.tgz" to "cce1dae6504260897c23e160bd8e796fde8f3687db87aa8fb36ce6604fc88154"),
            "armeabi-v7a" to ("pdfium-v8-android-arm.tgz" to "ee1ec168253df8934ba4a9d0bc072331011b4b8f5984485cd859ab2a8ea15524"),
            "x86_64" to ("pdfium-v8-android-x64.tgz" to "da4129fb59b7f2d8daa5d2973aa7e39d38bf94889482cfd124ee5109c73ceeb6"),
            "x86" to ("pdfium-v8-android-x86.tgz" to "c88734d92043c15f159974ad39e258f9504b93ce40ceebf04f820f3166f1d2fe"),
        )
    }

    override fun apply(target: Project) {
        with(target) {
            // Outside build/ so `clean` doesn't download again.
            val pdfiumDir = rootProject.layout.projectDirectory.dir(".gradle/pdfium/${RELEASE.replace('/', '-')}")
            val downloads = ARCHIVES.map { (abi, archive) ->
                val (fileName, hash) = archive
                tasks.register<DownloadPdfiumTask>("downloadPdfium" + abi.split('-', '_').joinToString("") { it.replaceFirstChar(Char::uppercase) }) {
                    url.set("$RELEASES/$RELEASE/$fileName")
                    sha256.set(hash)
                    outputDir.set(pdfiumDir.dir(abi))
                }
            }

            extensions.configure<LibraryExtension> {
                defaultConfig {
                    ndk.abiFilters += ARCHIVES.keys
                    externalNativeBuild.cmake.arguments += "-DPDFIUM_DIR=${pdfiumDir.asFile.invariantSeparatorsPath}"
                }
                externalNativeBuild.cmake.path = file("src/main/cpp/CMakeLists.txt")
            }

            tasks.matching { it.name.startsWith("configureCMake") }.configureEach {
                dependsOn(downloads)
            }

            // The app shows these texts; they come from the archive, so a new
            // RELEASE brings its own licenses along.
            val licenses = tasks.register<CopyPdfiumLicensesTask>("copyPdfiumLicenses") {
                archiveDir.set(downloads.first().flatMap { it.outputDir })
                outputDir.set(layout.buildDirectory.dir("generated/pdfiumLicenses"))
            }
            extensions.configure<LibraryAndroidComponentsExtension> {
                onVariants { variant ->
                    variant.sources.assets?.addGeneratedSourceDirectory(licenses, CopyPdfiumLicensesTask::outputDir)
                }
            }
        }
    }
}
