import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    checkstyle
}

val libs = the<LibrariesForLibs>()

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    configDirectory = isolated.rootProject.projectDirectory.dir(".checkstyle")
    maxWarnings = 0
}

val buildDirectory = layout.buildDirectory.get().asFile

tasks.withType<Checkstyle>().configureEach {
    exclude { it.file.startsWith(buildDirectory) }
}
