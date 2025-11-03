@file:Suppress("UnstableApiUsage")

require(gradle.gradleVersion == "8.12") {
    "Gradle version 8.12 required (current version: ${gradle.gradleVersion})"
}

plugins {
    alias(libs.plugins.library)
    id("maven-publish")
}

// project.name ("oboe") defined in settings.gradle.kts
project.group = "com.google.oboe"
project.version = "1.10.0-patch1"

val abis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

android {
    namespace = "${project.group}.${project.name}"
    compileSdk = libs.versions.compilesdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minsdk.get().toInt()

        buildToolsVersion = libs.versions.buildtools.get()
        ndkVersion = libs.versions.ndk.get()
        ndk {
            abiFilters += abis
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"

                arguments += "-DBUILD_SHARED_LIBS=true"
                arguments += "-DANDROID_PLATFORM=android-${libs.versions.minsdk.get()}"

                // arguments += "-DCMAKE_VERBOSE_MAKEFILE=ON"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("$projectDir/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    buildFeatures {
        prefabPublishing = true
    }

    prefab {
        create("oboe") {
            headers = "include"
        }
    }

    packaging {
        // avoids duplicating libs in .aar due to using prefab
        jniLibs {
            excludes += "**/*"
        }
    }
}

dependencies {
}

tasks.named<Delete>("clean") {
    delete.add(".cxx")
}

afterEvaluate {
    tasks.named("preBuild") {
        mustRunAfter("clean")
    }
    tasks.named("generatePomFileFor${project.name.cap()}Publication") {
        mustRunAfter("assembleRelease")
    }
    tasks.named("publish") {
        dependsOn("clean", "assembleRelease")
    }
}


publishing {
    val projectName = project.name
    val githubPackagesUrl = "https://maven.pkg.github.com/jg-hot/oboe"

    repositories {
        maven {
            url = uri(githubPackagesUrl)
            credentials {
                username = properties["gpr.user"]?.toString()
                password = properties["gpr.key"]?.toString()
            }
        }
    }

    publications {
        create<MavenPublication>(projectName) {
            artifact(layout.buildDirectory.file("outputs/aar/$projectName-release.aar"))
            artifactId = "$projectName-patched"

            pom {
                name = "$projectName-patched"
                description = "The AAR for Oboe - patched."
                licenses {
                    license {
                        name = "The Oboe License"
                        url = "https://github.com/google/oboe/blob/main/LICENSE"
                        distribution = "repo"
                    }
                    developers {
                        developer {
                            name = "The Android Open Source Project"
                        }
                    }
                    scm {
                        connection = "scm:git:https://github.com/jg-hot/oboe"
                        url = "https://github.com/jg-hot/oboe"
                    }
                }
            }
        }
    }
}

// capitalize the first letter to make task names matched when written in camel case
fun String.cap(): String = this.replaceFirstChar { it.uppercase() }
