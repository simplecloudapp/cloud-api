import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("cloud-api.platform-conventions")
    alias(libs.plugins.fabric.loom)
}

val shade = configurations.create("shade")
val minecraftVersions =
    listOf(
        libs.versions.fabric.minecraft
            .get(),
        "26.3",
    )

dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.fabric.minecraft.get()}")

    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)

    implementation(project(":platform:shared"))
    implementation(project(":api"))

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
                property("minecraftVersionRange", ">=${minecraftVersions.first()} <=${minecraftVersions.last()}")
            }
        }
    }
}

modrinth {
    gameVersions = minecraftVersions
    loaders.addAll("fabric", "quilt")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}
