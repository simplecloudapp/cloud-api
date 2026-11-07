import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("cloud-api.shadow-conventions")
    id("com.modrinth.minotaur")
    id("net.kyori.blossom")
}

sourceSets {
    main {
        blossom {
            resources {
                property("version", project.version.toString())
            }
        }
    }
}

val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    dependsOn(":api:shadowJar")

    exclude("META-INF/*.kotlin_module")
    exclude("META-INF/proguard/**")
    exclude("META-INF/versions/**")

    archiveFileName = "${isolated.rootProject.name}-${project.name}.jar"
}

modrinth {
    token = providers.gradleProperty("modrinthToken").orElse(providers.environmentVariable("MODRINTH_TOKEN"))
    projectId = "JCJKZvY2"
    versionNumber = version.toString()
    versionType = "release"
    uploadFile.set(shadowJar)
    changelog = "https://docs.simplecloud.app/changelog"
    syncBodyFrom = providers.fileContents(isolated.rootProject.projectDirectory.file("README.md")).asText
    gameVersions.addAll(
        "1.20",
        "1.20.1",
        "1.20.2",
        "1.20.3",
        "1.20.4",
        "1.20.5",
        "1.20.6",
        "1.21",
        "1.21.1",
        "1.21.2",
        "1.21.3",
        "1.21.4",
        "1.21.5",
        "1.21.6",
        "1.21.7",
        "1.21.8",
        "1.21.9",
        "1.21.10",
        "1.21.11",
        "26.1",
        "26.1.1",
        "26.1.2",
        "26.2",
        "26.3",
    )
}
