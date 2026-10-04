package me.nagasonic.alkatraz.spells;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Test-scope roster discovery for the wave-4 test suite.
 *
 * <p>The roster is 38 spell ymls today and 68 after wave 5, so no wave-4 test may
 * hardcode the count. Every wave-4 test iterates {@link #spellIds()} instead.
 *
 * <p>Discovery is two-tier. The primary source is the module's on-disk resource
 * directory, found from the Maven module basedir (Surefire runs with the working
 * directory set to {@code core/}). The fallback lists the {@code /spells} classpath
 * directory, which works when the tests run against an unpacked jar. If neither
 * resolves, {@link #rosterIsDiscoverable()} is false and the calling test must
 * {@code Assumptions.assumeTrue} rather than silently iterate an empty set.
 */
final class BundledSpellRoster {

    private static final Pattern REGISTRY_ID = Pattern.compile("registerIfEnabled\\(\"([a-z0-9_]+)\"");
    private static final Pattern SAVED_CONFIG_ID = Pattern.compile("\"spells/([a-z0-9_]+)\\.yml\"");

    private BundledSpellRoster() {
    }

    /** True when at least one roster source resolves. Never call {@link #spellIds()} without checking. */
    static boolean rosterIsDiscoverable() {
        return resourcesDir() != null || classpathDir() != null;
    }

    /**
     * Every bundled spell id, sorted, excluding {@code *_options.yml}. The order is
     * stable so a failure message is reproducible.
     */
    static List<String> spellIds() {
        Path dir = resourcesDir();
        if (dir != null) {
            try (Stream<Path> files = Files.list(dir)) {
                return files.map(p -> p.getFileName().toString())
                        .filter(BundledSpellRoster::isSpellYml)
                        .map(n -> n.substring(0, n.length() - 4))
                        .sorted()
                        .toList();
            } catch (IOException ignored) {
                // fall through to the classpath
            }
        }
        URL classpath = classpathDir();
        if (classpath == null) {
            throw new IllegalStateException(
                    "neither src/main/resources/spells nor the /spells classpath directory resolved; "
                            + "guard with rosterIsDiscoverable() before calling spellIds()");
        }
        String base = classpath.toExternalForm();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        List<String> ids = new ArrayList<>();
        try (InputStream listing = classpath.openStream()) {
            String body = new String(listing.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : body.split("\n")) {
                String name = line.trim();
                if (name.endsWith(".yml") && !name.endsWith("_options.yml")) {
                    ids.add(name.substring(0, name.length() - 4));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not list the /spells classpath directory", e);
        }
        ids.sort(Comparator.naturalOrder());
        return List.copyOf(ids);
    }

    static boolean has(String id) {
        return Files.isRegularFile(resourcesDir() == null
                ? Path.of("no-such-dir", id + ".yml")
                : resourcesDir().resolve(id + ".yml"));
    }

    /** Reads a bundled spell yml from the classpath, exactly as the config tests do. */
    static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = BundledSpellRoster.class.getResourceAsStream("/spells/" + id + ".yml")) {
            if (in == null) throw new IOException("missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /**
     * Resolves a path inside the {@code core} module. Surefire's working directory is
     * the module basedir, so the direct path is the normal case; the {@code ../core}
     * fallback covers an IDE runner whose working directory is the repository root.
     */
    static Path moduleFile(String relative) {
        Path direct = Path.of(relative);
        if (Files.exists(direct)) return direct;
        Path fromRepoRoot = Path.of("core").resolve(relative);
        return Files.exists(fromRepoRoot) ? fromRepoRoot : direct;
    }

    static String readSource(String relative) throws IOException {
        return Files.readString(moduleFile(relative), StandardCharsets.UTF_8);
    }

    /**
     * Every id passed to {@code registerIfEnabled} in {@code SpellRegistry.java}.
     */
    static List<String> registryIds() throws IOException {
        String source = readSource("src/main/java/me/nagasonic/alkatraz/spells/SpellRegistry.java");
        return distinctMatches(REGISTRY_ID, source);
    }

    /**
     * Every {@code spells/<id>.yml} literal in {@code Alkatraz.java}.
     *
     * <p>This deliberately matches the <em>literal</em> rather than the
     * {@code saveConfig("spells/...")} call, because spec section 10.3 converts
     * {@code saveSpellConfigs()} from 39 hardcoded calls into a loop over a
     * {@code spellConfigPaths()} set in wave 7. After that change there are no
     * {@code saveConfig} calls left to match, but the literals are still there.
     */
    static List<String> savedSpellConfigIds() throws IOException {
        String source = readSource("src/main/java/me/nagasonic/alkatraz/Alkatraz.java");
        return distinctMatches(SAVED_CONFIG_ID, source);
    }

    private static List<String> distinctMatches(Pattern pattern, String source) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            String id = matcher.group(1);
            if (id.endsWith("_options")) continue;
            found.add(id);
        }
        List<String> ids = new ArrayList<>(found);
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    private static boolean isSpellYml(String name) {
        return name.endsWith(".yml") && !name.endsWith("_options.yml");
    }

    private static Path resourcesDir() {
        Path direct = moduleFile("src/main/resources/spells");
        return Files.isDirectory(direct) ? direct : null;
    }

    private static URL classpathDir() {
        URL url = BundledSpellRoster.class.getResource("/spells");
        return url != null && "file".equals(url.getProtocol()) ? url : null;
    }
}