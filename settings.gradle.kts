pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android (open-source offline OCR fallback) is published via JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ClearPDF"
include(":backdrop")
include(":pdf-core")
include(":ocr-core")
include(":app")

