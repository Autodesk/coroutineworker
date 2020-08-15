import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.gradle.tasks.KotlinTest
import org.jetbrains.kotlin.konan.target.HostManager

val coroutinesVersion = "1.3.9"
val atomicfuVersion = "0.14.4"

plugins {
    kotlin("multiplatform") version "1.4.0"
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

        js {
            compilations.all {
                kotlinOptions {
                    moduleKind = "commonjs"
                }
            }
            browser()
            nodejs()
        }

        val nativeTargets = mutableListOf<KotlinNativeTarget>()

        iosX64 { nativeTargets.add(this) }
        iosArm64 { nativeTargets.add(this) }
        iosArm32 { nativeTargets.add(this) }
        macosX64 { nativeTargets.add(this) }
        mingwX64 { nativeTargets.add(this) }

        nativeTargets.forEach {
            val main by it.compilations.getting {
                kotlinOptions.freeCompilerArgs = listOf("-Xuse-experimental=kotlin.Experimental")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")
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
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        val appleMain by creating

        listOf("iosX64", "iosArm64", "iosArm32", "macosX64").forEach {
            getByName("${it}Main") {
                dependsOn(appleMain)
            }
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
    ktlintConfig("com.pinterest:ktlint:0.37.2")
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

apply("publish.gradle")
