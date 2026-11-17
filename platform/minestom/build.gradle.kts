import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("cloud-api.shadow-conventions")
}

dependencies {
    compileOnly(libs.minestom)
    compileOnly(libs.slf4j.api)
    implementation(project(":platform:shared"))
    implementation(project(":api")) {
        exclude(group = "net.kyori")
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

tasks.named<Jar>("jar") {
    archiveClassifier = "thin"
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = ""

    exclude("META-INF/*.kotlin_module")
    exclude("META-INF/proguard/**")
    exclude("META-INF/versions/**")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "minestom"
            artifact(tasks.named<ShadowJar>("shadowJar"))
            artifact(tasks.named<Jar>("sourcesJar"))

            pom {
                name = "SimpleCloud API Minestom"
                description = "SimpleCloud API provider for Minestom servers"
                url = "https://github.com/simplecloudapp/cloud-api"

                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }

                scm {
                    connection = "scm:git:git://github.com/simplecloudapp/cloud-api.git"
                    developerConnection = "scm:git:ssh://github.com/simplecloudapp/cloud-api.git"
                    url = "https://github.com/simplecloudapp/cloud-api"
                }
            }
        }
    }
}
