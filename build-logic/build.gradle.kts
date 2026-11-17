plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(plugin(libs.plugins.shadow))
    implementation(plugin(libs.plugins.minotaur))
    implementation(plugin(libs.plugins.blossom))
}

fun plugin(plugin: Provider<PluginDependency>): String {
    val dependency = plugin.get()
    return "${dependency.pluginId}:${dependency.pluginId}.gradle.plugin:${dependency.version.requiredVersion}"
}
