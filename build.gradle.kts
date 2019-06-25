plugins {
    kotlin("multiplatform") version "1.3.40"
    id("org.jetbrains.dokka") version "0.9.18"
    id("maven-publish")
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

//// iOS Test Runner
//
//val simulatorName = "iPad Air 2"
//val testExeTask by tasks.linkTestDebugExecutableIosX64
//
//tasks.register("testIos", Exec) {
//    group = "verification"
//    dependsOn testExeTask
//    executable "xcrun"
//    args = [
//            "simctl",
//            "spawn",
//            simulatorName,
//            testExeTask.outputFile.get()
//    ]
//}
//
//tasks.getByName("check") {
//    configure {
//        dependsOn "testIos"
//    }
//}
//
//    configurations {
//        compileClasspath
//        ktlint
//    }
//
//    dependencies {
//        ktlint "com.pinterest:ktlint:0.32.0"
//    }
//
//    def ktlint = tasks.register("ktlint", JavaExec) {
//    group = "verification"
//    def ktlint_args = project.findProperty("ktlint_args") ?: "--reporter=plain?group_by_file --reporter=checkstyle,output=${buildDir}/ktlint.xml src/**/*.kt"
//    description = "Check Kotlin code style."
//    classpath = configurations.ktlint
//    main = "com.pinterest.ktlint.Main"
//    args ktlint_args.split()
//}
//
//    check.configure {
//        dependsOn ktlint
//    }
//
//    tasks.register("ktlintFormat", JavaExec) {
//        group = "formatting"
//        description = "Fix Kotlin code style deviations."
//        classpath = configurations.ktlint
//        main = "com.pinterest.ktlint.Main"
//        args "-F", "src/**/*.kt"
//    }
//
//    def getDeployVersion() {
//        return project.findProperty("DEPLOY_VERSION") ?: VERSION
//    }
//
//    group = "com.autodesk"
//    version = getDeployVersion()
//
//    afterEvaluate {
//        publishing {
//            publications {
//                all {
//                    groupId = group
//                    version getDeployVersion()
//                    pom.withXml {
//                        def root = asNode()
//                        root.children().last() + {
//                            resolveStrategy = Closure.DELEGATE_FIRST
//
//                            description "Native coroutine-based workers"
//                            name = project.name
//                            url "https://github.com/autodesk/coroutineworker"
//                            scm {
//                                url "https://github.com/autodesk/coroutineworker"
//                                connection "scm:git:git://github.com/autodesk/coroutineworker.git"
//                                developerConnection "scm:git:ssh://git@github.com/autodesk/coroutineworker.git"
//                            }
//                            developers {
//                                developer {
//                                    id "autodesk"
//                                    name = "Autodesk"
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//
////TODO(basher): update for jcenter
////        repositories {
////            maven {
////                credentials {
////                    username
////                    password
////                }
////                url = ""
////            }
////        }
//        }
//    }
//}
//
