plugins {
    `kotlin-dsl`
    alias(libs.plugins.spotless)
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(plugin(libs.plugins.shadow))
    implementation(plugin(libs.plugins.minotaur))
    implementation(plugin(libs.plugins.spotless))
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "src/**/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

fun plugin(plugin: Provider<PluginDependency>): String {
    val dependency = plugin.get()
    return "${dependency.pluginId}:${dependency.pluginId}.gradle.plugin:${dependency.version.requiredVersion}"
}
