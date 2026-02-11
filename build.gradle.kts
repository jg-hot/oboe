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
project.version = "1.10.0-patch2"

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

    buildTypes {
        create("asan") {
            initWith(getByName("release"))

            externalNativeBuild {
                cmake {
                    arguments += "-DSANITIZE=asan"
                }
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

    publishing {
        singleVariant("release")
        singleVariant("asan")
    }
}

dependencies {
}

tasks.named<Delete>("clean") {
    delete.add(".cxx")
}

tasks.named("publish") {
    dependsOn("clean")
}

// afterEvaluate is required:
// https://developer.android.com/reference/tools/gradle-api/8.6/com/android/build/api/dsl/LibraryPublishing
afterEvaluate {
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
            val createPom: MavenPublication.(type: String) -> Unit = { type ->
                pom {
                    name = "$projectName-patched"
                    description = "The AAR for Oboe - patched ($type build)"
                    licenses {
                        license {
                            name = "The Oboe License"
                            url = "https://github.com/google/oboe/blob/main/LICENSE"
                            distribution = "repo"
                        }
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
            create<MavenPublication>("release") {
                from(components["release"])
                // artifact(layout.buildDirectory.file("outputs/aar/$projectName-release.aar"))
                artifactId = "$projectName-patched"
                createPom("release")
            }
            create<MavenPublication>("asan") {
                 from(components["asan"])
                // artifact(layout.buildDirectory.file("outputs/aar/$projectName-asan.aar"))
                artifactId = "$projectName-patched-asan"
                createPom("ASAN")
            }
        }
    }
}

// capitalize the first letter to make task names matched when written in camel case
fun String.cap(): String = this.replaceFirstChar { it.uppercase() }
