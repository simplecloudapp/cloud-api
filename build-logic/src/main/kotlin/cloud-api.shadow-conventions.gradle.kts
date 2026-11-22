import app.simplecloud.gradle.Relocations
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("cloud-api.base-conventions")
}

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()

    Relocations.packages.forEach { (source, target) ->
        relocate(source, target) {
            Relocations.excludes[source]?.forEach { exclude(it) }
        }
    }

    exclude {
        !it.isDirectory &&
            it.path.startsWith("META-INF/native-image/") &&
            !it.path.startsWith("META-INF/native-image/app.simplecloud.api/")
    }
}
