package app.simplecloud.api.provider.paper

import app.simplecloud.generated.SimpleCloudArtifacts
import io.papermc.paper.plugin.loader.PluginClasspathBuilder
import io.papermc.paper.plugin.loader.PluginLoader
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository

@Suppress("UnstableApiUsage")
class PaperLoader : PluginLoader {
    override fun classloader(classpathBuilder: PluginClasspathBuilder) {
        val resolver = MavenLibraryResolver()
        resolver.addDependency(
            Dependency(
                DefaultArtifact("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0"), null
            )
        )
        resolver.addRepository(
            RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2").build()
        )
        resolver.addRepository(
            RemoteRepository.Builder(
                "simplecloud",
                "default",
                "https://repo.simplecloud.app/snapshots"
            ).build()
        )
        resolver.addRepository(
            RemoteRepository.Builder(
                "buf",
                "default",
                "https://buf.build/gen/maven"
            ).build()
        )
        SimpleCloudArtifacts.artifacts.forEach { artifact ->
            resolver.addDependency(
                Dependency(
                    DefaultArtifact(artifact), null
                )
            )
        }
        classpathBuilder.addLibrary(resolver)
    }
}