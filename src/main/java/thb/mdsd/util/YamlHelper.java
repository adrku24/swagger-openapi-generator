package thb.mdsd.util;

import lombok.NonNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * This is a simple Yaml helper that converts string-paths like: "foo.bar" into map structures that the org.yaml library can handle.
 */
public class YamlHelper {

    private final File file;
    private final Map<String, Object> data;

    public YamlHelper(@NonNull File file) {
        this.file = file;
        this.data = this.loadYamlData();
    }

    private Map<String, Object> loadYamlData() {
        try (final InputStream inputStream = new FileInputStream(this.file)) {
            final HashMap<String, Object> temp = new Yaml().load(inputStream);
            return temp == null ? new HashMap<>() : temp;
        } catch (IOException exception) {
            // Ignore
        }

        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    public void set(@NonNull String key, Object value) {
        String[] keys = key.split("\\.");
        Map<String, Object> currentMap = this.data;

        for (int i = 0; i < keys.length - 1; i++) {
            currentMap = (Map<String, Object>) currentMap.computeIfAbsent(keys[i], _ -> new HashMap<>());
        }

        currentMap.put(keys[keys.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    public Object get(@NonNull String key) {
        final String[] keys = key.split("\\.");
        Map<String, Object> currentMap = this.data;

        for (String k : keys) {
            if (currentMap.containsKey(k)) {
                currentMap = (Map<String, Object>) currentMap.get(k);
            } else {
                return null;
            }
        }

        return currentMap;
    }

    public void save() {
        try (final FileWriter writer = new FileWriter(file)) {
            final DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setIndent(2);

            final Yaml yamlWithOptions = new Yaml(options);
            yamlWithOptions.dump(data, writer);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
