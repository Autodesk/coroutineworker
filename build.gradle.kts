import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

val coroutinesVersion = "1.3.9"
val atomicfuVersion = "0.14.4"

plugins {
    kotlin("multiplatform") version "1.4.21"
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
    explicitApi()
    targets {
        jvm {
            compilations.all {
                kotlinOptions {
                    jvmTarget = "1.8"
                }
            }
        }

        js(BOTH) {
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
        linuxX64 { nativeTargets.add(this) }

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
                implementation(kotlin("test-multiplatform"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")
            }
        }
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }

        val appleMain by creating

        listOf("iosX64", "iosArm64", "iosArm32", "macosX64").forEach {
            getByName("${it}Main") {
                dependsOn(appleMain)
            }
        }

        listOf("iosX64", "iosArm64", "iosArm32", "macosX64", "mingwX64", "linuxX64").forEach {
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
    ktlintConfig("com.pinterest:ktlint:0.40.0")
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
    args = listOf("-F", "src/**/*.kt", "*.kts")
}

val checkTask = tasks.named("check")
checkTask.configure {
    dependsOn(ktlint)
}

apply("publish.gradle")
