import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins {
    kotlin("multiplatform") version "1.3.40"
    id("org.jetbrains.dokka") version "0.9.18"
    id("maven-publish")
    id("signing")
}

repositories {
    google()
    jcenter()
    mavenCentral()
}

val coroutinesVersion: String by project
val dokkaVersion: String by project
val kotlinVersion: String by project
val statelyVersion: String by project

kotlin {
    targets {
        jvm()
        iosX64("iosSim")
        iosArm64("iosDevice64")
        mingwX64("mingw")
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:${coroutinesVersion}")
                implementation("co.touchlab:stately:${statelyVersion}")
            }
        }
        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-common")
                implementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:${coroutinesVersion}")
                implementation("co.touchlab:stately:${statelyVersion}")
            }
        }

         val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutinesVersion}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test:${kotlinVersion}")
                implementation("org.jetbrains.kotlin:kotlin-test-junit:${kotlinVersion}")
            }
        }

        val iosDevice64Main by getting {
            dependencies {
                kotlin.srcDirs("src/nativeMain/kotlin")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:${coroutinesVersion}")
            }
        }
        val iosDevice64Test by getting { kotlin.srcDir("src/cocoaTest/kotlin") }
        val iosSimMain by getting {
            dependencies {
                kotlin.srcDirs("src/nativeMain/kotlin")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:${coroutinesVersion}")
            }
        }
        val iosSimTest by getting { kotlin.srcDir("src/cocoaTest/kotlin") }

        val mingwMain by getting {
            dependencies {
                kotlin.srcDirs(
                    listOf(
                        "src/nativeMain/kotlin",
                        "src/mingwMain/kotlin"
                    )
                )
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:${coroutinesVersion}}")
            }
        }
    }
}

kotlin {
    targets.all {
        compilations.all {
            kotlinOptions.allWarningsAsErrors = true
        }
    }
}

// iOS Test Runner

val ktlintConfig by configurations.creating

dependencies {
    ktlintConfig("com.pinterest:ktlint:0.32.0")
}

val ktlint by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Check Kotlin code style."
    classpath = ktlintConfig
    main = "com.pinterest.ktlint.Main"
    args = listOf("src/**/*.kt")
}

val ktlintformat by tasks.registering(JavaExec::class) {
    group = "formatting"
    description = "Fix Kotlin code style deviations."
    classpath = ktlintConfig
    main = "com.pinterest.ktlint.Main"
    args = listOf("-F", "src/**/*.kt")
}

tasks.getByName("check") {
    configure {
        dependsOn(ktlint)
    }
}

val linkDebugTestIosSim by tasks.getting(KotlinNativeLink::class)
val testIosSim by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn(linkDebugTestIosSim)
    executable = "xcrun"
    setArgs(listOf(
        "simctl",
        "spawn",
        "iPad Air 2",
        linkDebugTestIosSim.outputFile.get()
    ))
}

tasks.getByName("check") {
    configure {
        dependsOn(testIosSim)
    }
}

apply("publish.gradle")