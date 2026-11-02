plugins {
    id("cloud-api.platform-conventions")
}

dependencies {
    compileOnly(libs.paper.api)
    implementation(project(":platform:shared"))
    implementation(project(":api")) {
        exclude(group = "net.kyori")
    }
}

val generateArtifactsClass = tasks.register("generateArtifactsClass") {
    group = "generation"
    description = "Generates a Java class containing artifact information"

    val outputDir = layout.buildDirectory.dir("generated/sources/artifacts/java/main")
    val outputFile = outputDir.map { it.file("app/simplecloud/generated/SimpleCloudArtifacts.java") }
    val controllerProtoVersion = libs.versions.controller.proto.get()
    val adventureProtoVersion = libs.versions.adventure.proto.get()

    outputs.dir(outputDir)

    doLast {
        val outputFileFile = outputFile.get().asFile

        outputFileFile.parentFile.mkdirs()
        outputFileFile.writeText(
            """
            package app.simplecloud.generated;

            import java.util.List;

            public class SimpleCloudArtifacts {
                // StringBuilder prevents shadow relocation of maven coordinates (not inlined by javac)
                // Split "build.buf" to avoid shadow matching
                public static final List<String> artifacts = List.of(
                    new StringBuilder("build.").append("buf.gen:simplecloud_controller_protocolbuffers_java_lite:$controllerProtoVersion").toString(),
                    new StringBuilder("build.").append("buf.gen:simplecloud_adventure_protocolbuffers_java_lite:$adventureProtoVersion").toString()
                );
            }
            """.trimIndent()
        )
        println("Generated Artifacts.java at ${outputFileFile.absolutePath}")
    }
}

sourceSets.main {
    java.srcDir(generateArtifactsClass)
}

modrinth {
    loaders.add("paper")
}
