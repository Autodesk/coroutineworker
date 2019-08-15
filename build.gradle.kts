import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.HostManager

val coroutinesVersion by extra("1.3.0-RC2")
val dokkaVersion by extra("0.9.18")
val kotlinVersion by extra("1.3.41")
val statelyVersion by extra("0.7.3")

plugins {
    kotlin("multiplatform") version "1.3.41"
    id("org.jetbrains.dokka") version "0.9.18"
    id("maven-publish")
    id("signing")
}

repositories {
    google()
    jcenter()
    mavenCentral()
}

kotlin {
    targets {
        jvm()
        iosX64()
        iosArm64()
        iosArm32()
        mingwX64()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:$coroutinesVersion")
                implementation("co.touchlab:stately:$statelyVersion")
            }
        }
        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-common")
                implementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:$coroutinesVersion")
                implementation("co.touchlab:stately:$statelyVersion")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
                implementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
            }
        }
        val nativeMain by creating {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:$coroutinesVersion")
            }
        }
        val nativeTest by creating {}

        listOf("iosX64", "iosArm64", "iosArm32", "mingwX64").forEach {
            getByName("${it}Main") {
                dependsOn(nativeMain)
            }
            getByName("${it}Test") {
                dependsOn(nativeTest)
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

val checkTask = tasks.named("check")
checkTask.configure {
    dependsOn(ktlint)
}

// iOS Test Runner
if (HostManager.hostIsMac) {
    val linkDebugTestIosX64 by tasks.getting(KotlinNativeLink::class)
    val testIosSim by tasks.registering(Exec::class) {
        group = "verification"
        dependsOn(linkDebugTestIosX64)
        executable = "xcrun"
        setArgs(
            listOf(
                "simctl",
                "spawn",
                "-s",
                "iPad Air 2",
                linkDebugTestIosX64.outputFile.get()
            )
        )
    }

    checkTask.configure {
        dependsOn(testIosSim)
    }
}

apply("publish.gradle")
