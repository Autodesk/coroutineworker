import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.gradle.tasks.KotlinTest
import org.jetbrains.kotlin.konan.target.HostManager

val coroutinesVersion by extra("1.3.3")
val atomicfuVersion by extra("0.14.1")

plugins {
    kotlin("multiplatform") version "1.3.61"
    id("org.jetbrains.dokka") version "0.10.0"
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
        jvm {
            compilations.all {
                kotlinOptions {
                    jvmTarget = "1.8"
                }
            }
        }
        iosX64 {
            val main by compilations.getting {
                kotlinOptions.freeCompilerArgs = listOf("-Xuse-experimental=kotlin.Experimental")
            }
        }
        iosArm64 {
            val main by compilations.getting {
                kotlinOptions.freeCompilerArgs = listOf("-Xuse-experimental=kotlin.Experimental")
            }
        }
        iosArm32 {
            val main by compilations.getting {
                kotlinOptions.freeCompilerArgs = listOf("-Xuse-experimental=kotlin.Experimental")
            }
        }
        macosX64 {
            val main by compilations.getting {
                kotlinOptions.freeCompilerArgs = listOf("-Xuse-experimental=kotlin.Experimental")
            }
        }
        mingwX64 {
            val main by compilations.getting {
                kotlinOptions.freeCompilerArgs = listOf("-Xuse-experimental=kotlin.Experimental")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:$coroutinesVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:$coroutinesVersion")
                implementation("org.jetbrains.kotlinx:atomicfu-native:$atomicfuVersion")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }

        listOf("iosX64", "iosArm64", "iosArm32", "macosX64", "mingwX64").forEach {
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
    ktlintConfig("com.pinterest:ktlint:0.35.0")
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
