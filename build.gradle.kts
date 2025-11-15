import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    alias(libs.plugins.shadow)
    alias(libs.plugins.sonatype.central.portal.publisher)
    `maven-publish`
    `signing`
}

val baseVersion = "0.1.0-platform.1"
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
    apply(plugin = "maven-publish")
    apply(plugin = "com.gradleup.shadow")
    apply(plugin = "org.gradle.signing")

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
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
