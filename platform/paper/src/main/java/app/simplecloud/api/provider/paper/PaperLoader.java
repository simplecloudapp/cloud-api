package app.simplecloud.api.provider.paper;

import app.simplecloud.generated.SimpleCloudArtifacts;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

@SuppressWarnings("UnstableApiUsage")
public class PaperLoader implements PluginLoader {
    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(
            new RemoteRepository.Builder("central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build()
        );
        resolver.addRepository(
            new RemoteRepository.Builder(
                "simplecloud",
                "default",
                "https://repo.simplecloud.app/snapshots"
            ).build()
        );
        resolver.addRepository(
            new RemoteRepository.Builder(
                "buf",
                "default",
                "https://buf.build/gen/maven"
            ).build()
        );
        for (String artifact : SimpleCloudArtifacts.artifacts) {
            resolver.addDependency(
                new Dependency(
                    new DefaultArtifact(artifact), null
                )
            );
        }
        classpathBuilder.addLibrary(resolver);
    }
}

