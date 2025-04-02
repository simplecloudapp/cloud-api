import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlin.jvm)
}

group = "app.simplecloud.api"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven("https://repo.papermc.io/repository/maven-public")
        maven("https://repo.simplecloud.app/snapshots")
        maven("https://buf.build/gen/maven")
    }

}

tasks.named("shadowJar", ShadowJar::class).configure {
    enabled = false
}

subprojects {
    apply {
        plugin(rootProject.libs.plugins.shadow.get().pluginId)
        plugin(rootProject.libs.plugins.kotlin.jvm.get().pluginId)
    }
    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}