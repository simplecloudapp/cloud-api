plugins {
    alias(libs.plugins.minotaur)
}

dependencies {
    compileOnly(rootProject.libs.paper.api)
    implementation(project(":platform:shared"))
    implementation(project(":api")) {
        exclude(group = "net.kyori")
    }
}

sourceSets.main {
    java.srcDir(layout.buildDirectory.dir("generated/src/main/java"))
}

tasks.register("generateArtifactsClass") {
    group = "generation"
    description = "Generates a Java class containing artifact information"

    val outputDir = layout.buildDirectory.dir("generated/src/main/java/app/simplecloud/generated")
    val outputFile = outputDir.map { it.file("SimpleCloudArtifacts.java") }
    val controllerProtoVersion = rootProject.libs.versions.controller.proto.get()
    val adventureProtoVersion = rootProject.libs.versions.adventure.proto.get()
    val jnatsVersion = rootProject.libs.versions.jnats.get()

    outputs.file(outputFile)

    doLast {
        val outputDirFile = outputDir.get().asFile
        val outputFileFile = outputFile.get().asFile

        outputDirFile.mkdirs()
        outputFileFile.writeText(
            """
            package app.simplecloud.generated;
            
            import java.util.List;
            
            public class SimpleCloudArtifacts {
                // StringBuilder prevents shadow relocation of maven coordinates (not inlined by javac)
                // Split patterns: "build.buf" and "io.nats" to avoid shadow matching
                public static final List<String> artifacts = List.of(
                    new StringBuilder("build.").append("buf.gen:simplecloud_controller_protocolbuffers_java_lite:$controllerProtoVersion").toString(),
                    new StringBuilder("build.").append("buf.gen:simplecloud_adventure_protocolbuffers_java_lite:$adventureProtoVersion").toString(),
                    new StringBuilder("io.").append("nats:jnats:$jnatsVersion").toString()
                );
            }
            """.trimIndent()
        )
        println("Generated Artifacts.java at ${outputFileFile.absolutePath}")
    }
}

tasks.named("compileJava") {
    dependsOn("generateArtifactsClass")
}

modrinth {
    token.set(project.findProperty("modrinthToken") as String? ?: System.getenv("MODRINTH_TOKEN"))
    projectId.set("JCJKZvY2")
    versionNumber.set(rootProject.extra["modrinthVersion"] as String)
    versionType.set("release")
    uploadFile.set(tasks.shadowJar)
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
    )
    loaders.add("paper")
    changelog.set("https://docs.simplecloud.app/changelog")
    syncBodyFrom.set(rootProject.file("README.md").readText())
}
