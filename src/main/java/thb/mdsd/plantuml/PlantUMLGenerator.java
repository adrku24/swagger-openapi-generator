package thb.mdsd.plantuml;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PlantUMLGenerator {

    private static final Map<String, String> HTTP_COLORS = Map.of(
        "GET", "#6CC644",
        "POST", "#49CC90",
        "PUT", "#FCA130",
        "PATCH", "#B45AF2",
        "DELETE", "#F93E3E"
    );

    public static void generateFromProject(Path projectRoot, Path outputFile) throws IOException {
        final Path srcRoot = projectRoot.resolve("src/main/java");
        if (!Files.exists(srcRoot)) {
            throw new IOException("src/main/java nicht gefunden");
        }

        final String basePackage = detectBasePackage(srcRoot);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            writer.write("@startuml\n\n");
            Files.walk(srcRoot).filter(p -> p.toString().endsWith(".java")).forEach(p -> writeClass(writer, p, basePackage));
            writer.write("\n@enduml\n");
        }
    }

    private static void writeClass(final BufferedWriter writer, final Path javaFile, final String basePackage) {
        try {
            final List<String> lines = Files.readAllLines(javaFile);
            final String pkg = lines.stream()
                .filter(l -> l.startsWith("package "))
                .map(l -> l.replace("package", "").replace(";", "").trim())
                .findFirst()
                .orElse("");

            if (!pkg.startsWith(basePackage.substring(0, basePackage.length() - 1))) {
                return;
            }

            final String className = lines.stream()
                .filter(l -> l.contains(" class "))
                .map(l -> l.replaceAll(".*class\\s+(\\w+).*", "$1"))
                .findFirst()
                .orElse(null);

            if (className == null) return;

            writer.write("class " + pkg + "." + className + " {\n");

            String pendingHttpVerb = null;
            boolean insideRequestMapping = false;

            final Pattern shortcutMapping = Pattern.compile("@(Get|Post|Put|Patch|Delete)Mapping");
            final Pattern requestMethod = Pattern.compile("RequestMethod\\.(GET|POST|PUT|PATCH|DELETE)");
            final Pattern methodPattern = Pattern.compile("(public|protected|private)?\\s*\\S+\\s+\\w+\\s*\\([^)]*\\).*");

            for (String raw : lines) {
                final String line = raw.trim();
                final Matcher shortcut = shortcutMapping.matcher(line);

                if (shortcut.find()) {
                    pendingHttpVerb = shortcut.group(1).toUpperCase();
                    insideRequestMapping = false;
                    continue;
                }

                if (line.startsWith("@RequestMapping")) {
                    insideRequestMapping = true;
                    continue;
                }

                if (insideRequestMapping) {
                    final Matcher rm = requestMethod.matcher(line);
                    if (rm.find()) {
                        pendingHttpVerb = rm.group(1);
                    }

                    if (line.contains(")")) {
                        insideRequestMapping = false;
                    }

                    continue;
                }

                final Matcher methodMatcher = methodPattern.matcher(line);
                if (methodMatcher.matches() && !line.contains("class")) {
                    final String methodLine = sanitizeMethod(line);

                    if (pendingHttpVerb != null && HTTP_COLORS.containsKey(pendingHttpVerb)) {
                        writer.write("  <color: " + HTTP_COLORS.get(pendingHttpVerb) + "> " + "{method} " + methodLine + " </color>\n");
                    } else {
                        writer.write("  {method} " + methodLine + "\n");
                    }

                    pendingHttpVerb = null;
                }
            }

            writer.write("}\n\n");
        } catch (IOException ignored) {
        }
    }

    private static String sanitizeMethod(String line) {
        return line.replace("{", "").replace("}", "").replace(";", "").trim();
    }

    private static String detectBasePackage(Path srcRoot) throws IOException {
        try (Stream<Path> dirs = Files.list(srcRoot)) {
            return dirs.filter(Files::isDirectory).map(d -> d.getFileName().toString() + ".").findFirst().orElseThrow(() -> new IOException("Kein Base-Package gefunden"));
        }
    }
}
