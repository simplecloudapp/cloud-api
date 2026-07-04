package app.simplecloud.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

public abstract class FixOpenApiGeneratedCode extends DefaultTask {
    private static final Pattern WRITE_METHOD_PATTERN = Pattern.compile(
        "(// check if the actual instance is of the type `Object`\\s+if \\(value\\.getActualInstance\\(\\) instanceof Object\\) \\{[^}]+\\}\\s+)(// check if the actual instance is of the type `(\\w+)`\\s+if \\(value\\.getActualInstance\\(\\) instanceof \\3\\) \\{[^}]+\\})",
        Pattern.DOTALL
    );

    private static final Pattern SET_INSTANCE_PATTERN = Pattern.compile(
        "(public void setActualInstance\\(Object instance\\) \\{\\s+)(if \\(instance instanceof Object\\) \\{\\s+super\\.setActualInstance\\(instance\\);\\s+return;\\s+\\}\\s+)(if \\(instance instanceof (\\w+)\\) \\{)",
        Pattern.DOTALL
    );

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getModelsDir();

    @OutputFile
    public abstract RegularFileProperty getMarkerFile();

    @TaskAction
    public void fix() throws IOException {
        File[] modelFiles = getModelsDir().get().getAsFile().listFiles(
            file -> file.isFile() && file.getName().startsWith("V0") && file.getName().endsWith(".java")
        );

        if (modelFiles != null) {
            for (File file : modelFiles) {
                String content = Files.readString(file.toPath());
                String fixed = WRITE_METHOD_PATTERN.matcher(content).replaceAll("$2\n                    $1");
                fixed = SET_INSTANCE_PATTERN.matcher(fixed).replaceAll("$1$3");

                if (!fixed.equals(content)) {
                    Files.writeString(file.toPath(), fixed);
                }
            }
        }

        File markerFile = getMarkerFile().get().getAsFile();
        markerFile.getParentFile().mkdirs();
        Files.writeString(markerFile.toPath(), "fixed\n", StandardCharsets.UTF_8);
    }
}
