import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("cloud-api.platform-conventions")
    alias(libs.plugins.neoforge.moddev)
}

val shade = configurations.create("shade")
val minecraftVersions = listOf("26.2", "26.3")

neoForge {
    version = libs.versions.neoforge.get()
}

dependencies {
    implementation(project(":platform:shared")) {
        exclude(group = "com.google.code.gson")
        exclude(group = "org.jspecify")
    }
    implementation(project(":api")) {
        exclude(group = "com.google.code.gson")
        exclude(group = "org.jspecify")
    }

    shade(project(":platform:shared"))
    shade(project(":api"))
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shade)
}

sourceSets {
    main {
        blossom {
            resources {
                property("minecraftVersionRange", "[${minecraftVersions.first()},${minecraftVersions.last()}]")
                property("neoForgeVersion", libs.versions.neoforge.get())
            }
        }
    }
}

modrinth {
    gameVersions = minecraftVersions
    loaders.add("neoforge")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}
