import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")

    `maven-publish`
    alias(libs.plugins.openapi.generator)
    kotlin("jvm")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation(rootProject.libs.jnats)
    implementation(rootProject.libs.controller.proto)
    implementation(rootProject.libs.okhttp)
    implementation(rootProject.libs.okhttp.logging)
    implementation(rootProject.libs.gson)
    implementation(rootProject.libs.gson.fire)
    implementation(rootProject.libs.jakarta.annotation)
    implementation(rootProject.libs.javax.annotation)
}

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()

    val versionPath = project.version.toString().replace(".", "_").replace("-", "_")

    relocate("io.nats", "app.simplecloud.api.shaded.v${versionPath}.nats")
    relocate("com.google", "app.simplecloud.api.shaded.v${versionPath}.google")
    relocate("build.buf", "app.simplecloud.api.shaded.v${versionPath}.buf")
    relocate("okhttp3", "app.simplecloud.api.shaded.v${versionPath}.okhttp3")
    relocate("okio", "app.simplecloud.api.shaded.v${versionPath}.okio")
    relocate("io.gsonfire", "app.simplecloud.api.shaded.v${versionPath}.gsonfire")
    relocate("org.bouncycastle", "app.simplecloud.api.shaded.v${versionPath}.bouncycastle")
    relocate("org.intellij", "app.simplecloud.api.shaded.v${versionPath}.intellij")
    relocate("org.jetbrains", "app.simplecloud.api.shaded.v${versionPath}.jetbrains")

    relocate("google", "app.simplecloud.api.shaded.v${versionPath}.google")
    relocate("native", "app.simplecloud.api.shaded.v${versionPath}.native")
    relocate("core", "app.simplecloud.api.shaded.v${versionPath}.core")

    archiveClassifier.set("")
}

tasks.named("jar") {
    enabled = false
}

sourceSets {
    main {
        java {
            srcDir("$buildDir/generated/src/main/java")
        }
    }
}

openApiGenerate {
    generatorName.set("java")
    remoteInputSpec.set("https://controller.platform.simplecloud.app/swagger/doc.json")

    outputDir.set("$buildDir/generated")

//    generateSupportingFiles.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    generateApiTests.set(false)
    generateModelTests.set(false)

    apiPackage.set("app.simplecloud.api.web.apis")
    modelPackage.set("app.simplecloud.api.web.models")
    packageName.set("app.simplecloud.api.web")

    configOptions.set(
        mapOf(
            "library" to "okhttp-gson",
            "serializationLibrary" to "gson",
            "useJakartaEe" to "true"
        )
    )
}

tasks.compileJava {
    dependsOn("openApiGenerate")
}

tasks.named<Javadoc>("javadoc") {
    isFailOnError = false
    options {
        (this as StandardJavadocDocletOptions).apply {
            addStringOption("Xmaxerrs", "10000")
            addStringOption("Xmaxwarns", "10000")
        }
    }
}

tasks.named<Jar>("sourcesJar") {
    dependsOn("openApiGenerate")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.named<ShadowJar>("shadowJar")) {
                classifier = ""
            }

            artifact(tasks.named<Jar>("javadocJar"))
            artifact(tasks.named<Jar>("sourcesJar"))

            pom {
                name.set("SimpleCloud API")
                description.set("Cloud API for SimpleCloud")
                url.set("https://github.com/simplecloudapp/cloud-api")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("simplecloud")
                        name.set("SimpleCloud Team")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/simplecloudapp/cloud-api.git")
                    developerConnection.set("scm:git:ssh://github.com/simplecloudapp/cloud-api.git")
                    url.set("https://github.com/simplecloudapp/cloud-api")
                }

                withXml {
                    val root = asNode()

                    // Add dependencies node if it doesn't exist
                    var dependenciesNode = root["dependencies"] as? groovy.util.Node
                    if (dependenciesNode == null) {
                        dependenciesNode = root.appendNode("dependencies")
                    }

                    // Add proto dependency (needed for compilation since proto classes aren't relocated)
                    dependenciesNode?.let { deps ->
                        val protoDep = deps.appendNode("dependency")
                        protoDep.appendNode("groupId", "build.buf.gen")
                        protoDep.appendNode("artifactId", "simplecloud_controller_protocolbuffers_java_lite")
                        protoDep.appendNode("version", rootProject.libs.controller.proto.get().version.toString())
                        protoDep.appendNode("scope", "compile")
                    }

                    // Note: io.nats, com.squareup.okhttp3, com.google.code.gson, and io.gsonfire are NOT added because they're shaded
                }
            }
        }
    }
}

//signing {
//    val isSnapshot = project.version.toString().contains("dev")
//    if (!isSnapshot) {
//        sign(publishing.publications["maven"])
//        useGpgCmd()
//    }
//}

tasks.register<Javadoc>("generateJavadocSite") {
    dependsOn("compileJava")
    source = sourceSets["main"].allJava
    classpath = configurations["compileClasspath"]
    setDestinationDir(file("$projectDir/docs/javadoc"))
    isFailOnError = false

    options {
        (this as StandardJavadocDocletOptions).apply {
            links("https://docs.oracle.com/en/java/javase/21/docs/api/")
            addBooleanOption("html5", true)
            addStringOption("Xmaxerrs", "10000")
            addStringOption("Xmaxwarns", "10000")
        }
    }
}
repositories {
    mavenCentral()
}