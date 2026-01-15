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
    val outputFile = outputDir.get().file("SimpleCloudArtifacts.java")

    outputs.file(outputFile)

    doLast {
        outputDir.get().asFile.mkdirs()
        outputFile.asFile.writeText(
            """
            package app.simplecloud.generated;
            
            import java.util.List;
            
            public class SimpleCloudArtifacts {
                // StringBuilder prevents shadow relocation of maven coordinates (not inlined by javac)
                // Split patterns: "build.buf" and "io.nats" to avoid shadow matching
                public static final List<String> artifacts = List.of(
                    new StringBuilder("build.").append("buf.gen:simplecloud_controller_protocolbuffers_java_lite:${rootProject.libs.versions.controller.proto.get()}").toString(),
                    new StringBuilder("build.").append("buf.gen:simplecloud_adventure_protocolbuffers_java_lite:${rootProject.libs.versions.adventure.proto.get()}").toString(),
                    new StringBuilder("io.").append("nats:jnats:${rootProject.libs.versions.jnats.get()}").toString()
                );
            }
            """.trimIndent()
        )
        println("Generated Artifacts.java at ${outputFile.asFile.absolutePath}")
    }
}

tasks.named("compileJava") {
    dependsOn("generateArtifactsClass")
}

sourceSets.main {
    java.srcDir(layout.buildDirectory.dir("generated/src/main/java"))
}

