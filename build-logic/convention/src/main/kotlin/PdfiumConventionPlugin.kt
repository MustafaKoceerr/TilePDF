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
        const val RELEASE = "tilepdf-8076-2"

        /** Android ABI to the release archive and its SHA-256. */
        val ARCHIVES = mapOf(
            "arm64-v8a" to ("pdfium-v8-android-arm64.tgz" to "0853a9625e9d676ff60cc463e212f2dfce13ccc0095476abc1b9086122a59394"),
            "armeabi-v7a" to ("pdfium-v8-android-arm.tgz" to "18af9f03094552d642b6a9b6a1893afad12b17fd7b62c9734c1be1da122c0df1"),
            "x86_64" to ("pdfium-v8-android-x64.tgz" to "56099749e4c8df10c8842521d00914d3a85151c8f53a134de580bc44da6fe339"),
            "x86" to ("pdfium-v8-android-x86.tgz" to "9bbf8358a16a4d626076b69e2cdcda844c1dd0486a718929400888cd96e51e54"),
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
