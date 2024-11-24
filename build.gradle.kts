import ProjectVersions.openosrsVersion

buildscript {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    java // Enables annotationProcessor and implementation in dependencies
    checkstyle
}

// Apply custom plugins
apply<BootstrapPlugin>()

allprojects {
    group = "com.openosrs.externals"

    repositories {
        mavenCentral()
        jcenter()
        flatDir {
            dirs("libs") // Directory containing client.jar
        }
    }
}

dependencies {
    // Ensure client.jar is available for the main project
    compileOnly(fileTree("libs") {
        include("client.jar")
    })
}

subprojects {
    group = "com.openosrs.externals"

    // Define PluginProvider and PluginLicense for all subprojects
    project.extra["PluginProvider"] = "Papaya"
    project.extra["PluginLicense"] = "3-Clause BSD License"

    repositories {
        flatDir {
            dirs("libs")
        }

        jcenter {
            content {
                excludeGroupByRegex("com\\.openosrs.*")
            }
        }

        exclusiveContent {
            forRepository {
                flatDir {
                    dirs("libs")
                }
            }
            filter {
                includeGroupByRegex("com\\.openosrs.*")
            }
        }
    }

    apply<JavaPlugin>()

    dependencies {
        annotationProcessor(Libraries.lombok)
        annotationProcessor(Libraries.pf4j)

        compileOnly(fileTree("libs") {
            include("client.jar")
        })

        compileOnly(Libraries.findbugs)
        compileOnly(Libraries.apacheCommonsText)
        compileOnly(Libraries.gson)
        compileOnly(Libraries.guice)
        compileOnly(Libraries.lombok)
        compileOnly(Libraries.okhttp3)
        compileOnly(Libraries.pf4j)
        compileOnly(Libraries.rxjava)
    }

    tasks {
        withType<JavaCompile> {
            options.encoding = "UTF-8"
        }

        register<Copy>("copyDeps") {
            into("./build/deps/")
            from(configurations["runtimeClasspath"])
        }

        withType<Jar> {
            doLast {
                copy {
                    from("./build/libs/")
                    into("../releases/")
                }
            }
        }

        withType<AbstractArchiveTask> {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
            dirMode = 493
            fileMode = 420
        }
    }
}


// Add client.jar explicitly to all subprojects
gradle.projectsEvaluated {
    subprojects.forEach { project ->
        project.dependencies {
            compileOnly(fileTree("libs") {
                include("client.jar")
            })
        }
    }
}
