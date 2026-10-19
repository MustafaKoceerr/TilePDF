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
        const val RELEASE = "tilepdf-8076-4"

        /** Android ABI to the release archive and its SHA-256. */
        val ARCHIVES = mapOf(
            "arm64-v8a" to ("pdfium-v8-android-arm64.tgz" to "7898d135cbe5c75c0ba638ffb6ae7e5ad052173d34413974424deee3735bc573"),
            "armeabi-v7a" to ("pdfium-v8-android-arm.tgz" to "04a85fdef9cd6f8f19f6b30584c54229aa9ce8c6e3f65ac6b6d902e358afa72c"),
            "x86_64" to ("pdfium-v8-android-x64.tgz" to "687743daefb7da42443e8a1e7e4fbb2dc8fc9b10d12d2010eccf7748e17894dd"),
            "x86" to ("pdfium-v8-android-x86.tgz" to "44d72ac3a7188d8323ee478428c758f07408da707adc8cbe6c45ce98c3b0e45e"),
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
