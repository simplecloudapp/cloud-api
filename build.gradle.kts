import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("java-library")
    alias(libs.plugins.shadow)
    alias(libs.plugins.sonatype.central.portal.publisher)
    `maven-publish`
    `signing`
}

val baseVersion = "0.1.0-platform.9"
val commitHash = System.getenv("COMMIT_HASH")
val isSnapshot = commitHash != null

allprojects {
    group = "app.simplecloud.api"
    version = if (isSnapshot) "${baseVersion}-dev.${System.currentTimeMillis()}-${commitHash}" else baseVersion

    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven("https://repo.papermc.io/repository/maven-public")
        maven("https://repo.simplecloud.app/snapshots")
        maven("https://buf.build/gen/maven")
    }
}

tasks.named<ShadowJar>("shadowJar") {
    enabled = false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "com.gradleup.shadow")
    apply(plugin = "org.gradle.signing")

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    if (project.path.startsWith(":platform:") && project.path != ":platform:shared") {
        tasks.named<ShadowJar>("shadowJar") {
            dependsOn(":api:shadowJar")
            mergeServiceFiles()

            exclude("io/nats/**")
            exclude("com/google/**")
            exclude("build/buf/**")
            exclude("okhttp3/**")
            exclude("okio/**")
            exclude("io/gsonfire/**")
            exclude("org/bouncycastle/**")
            exclude("org/intellij/**")
            exclude("org/jetbrains/**")
            exclude("kotlin/**")
            exclude("google/**")
            exclude("native/**")
            exclude("core/**")
            exclude("META-INF/*.kotlin_module")
            exclude("META-INF/proguard/**")
            exclude("META-INF/versions/**")

            archiveFileName.set("${rootProject.name}-${project.name}.jar")
        }
    }

    publishing {
        repositories {
            maven {
                name = "simplecloud"
                url = uri("https://repo.simplecloud.app/snapshots/")
                credentials {
                    username = System.getenv("SIMPLECLOUD_USERNAME")
                    password = System.getenv("SIMPLECLOUD_PASSWORD")
                }
            }
        }
//        publications {
//            create<MavenPublication>("maven") {
//                from(components["java"])
//            }
//        }
    }

//    signing {
//        if (!isSnapshot) {
//            sign(publishing.publications["maven"])
//            useGpgCmd()
//        }
//    }
}

tasks.register("buildTemplates") {
    group = "build"
    description = "Builds all platform JARs and copies them to run/templates structure"

    val platformProjects = listOf("paper", "spigot", "spigot-legacy", "bungeecord", "velocity")

    platformProjects.forEach { platform ->
        dependsOn(":platform:$platform:shadowJar")
    }

    doLast {
        platformProjects.forEach { platform ->
            val shadowJarTask = project(":platform:$platform").tasks.named<ShadowJar>("shadowJar").get()
            val jarFile = shadowJarTask.archiveFile.get().asFile

            val folderName = platform.removeSuffix("-legacy")
            val templateDir = file("run/templates/every_$folderName/plugins")
            templateDir.mkdirs()

            val targetName = if (platform.endsWith("-legacy")) {
                "${rootProject.name}-${platform}.jar.legacy"
            } else {
                "${rootProject.name}-${platform}.jar"
            }

            jarFile.copyTo(File(templateDir, targetName), overwrite = true)
            println("Copied $jarFile to ${File(templateDir, targetName)}")
        }

        val waterfallDir = file("run/templates/every_waterfall")
        waterfallDir.deleteRecursively()
        file("run/templates/every_bungeecord").copyRecursively(waterfallDir)
        println("Copied every_bungeecord to every_waterfall")
    }
}

tasks.register<Zip>("zipTemplates") {
    group = "build"
    description = "Builds templates and creates a zip archive"

    dependsOn("buildTemplates")

    from("run/templates")
    archiveFileName.set("templates.zip")
    destinationDirectory.set(file("build"))
}
