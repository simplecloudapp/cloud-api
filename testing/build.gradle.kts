plugins {
    id("cloud-api.base-conventions")
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api(project(":api"))
    api(libs.junit.jupiter.api)
    api(libs.adventure.api)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "testing"
            from(components["java"])

            pom {
                name = "SimpleCloud API Testing"
                description = "Testing framework for the SimpleCloud API"
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
