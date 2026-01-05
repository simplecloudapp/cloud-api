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

    public static final String MAVEN_CENTRAL_DEFAULT_MIRROR = getDefaultMavenCentralMirror();

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(
            new RemoteRepository.Builder("central", "default", MAVEN_CENTRAL_DEFAULT_MIRROR).build()
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

    private static String getDefaultMavenCentralMirror() {
        String central = System.getenv("PAPER_DEFAULT_CENTRAL_REPOSITORY");
        if (central == null) {
            central = System.getProperty("org.bukkit.plugin.java.LibraryLoader.centralURL");
        }
        if (central == null) {
            central = "https://maven-central.storage-download.googleapis.com/maven2";
        }
        return central;
    }
}

