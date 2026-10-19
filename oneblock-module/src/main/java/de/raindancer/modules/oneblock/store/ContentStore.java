package de.raindancer.modules.oneblock.store;

import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.modules.oneblock.OneBlockModule;
import de.raindancer.modules.oneblock.model.Content;
import de.raindancer.modules.oneblock.model.IslandDimension;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The island server's {@code content.yml}: written from the shipped one the first time, read again on
 * {@code /islandadmin reload}. Whoever asks gets the content as it is now — a reload swaps it whole.
 */
public final class ContentStore {

    /** Where a content.yml from before phases is kept when the shipped phases replace it. */
    static final String BACKUP = "content-before-phases.yml";

    private final YamlStore store;
    private final Path file;
    private final ContentReader.BlockCheck check;
    private final LogChannel log;
    private final AtomicReference<Content> current = new AtomicReference<>();

    public ContentStore(Path file, LogChannel log) {
        this(file, log, ContentReader.ON_A_SERVER);
    }

    ContentStore(Path file, LogChannel log, ContentReader.BlockCheck check) {
        this.store = new YamlStore(file);
        this.file = file;
        this.log = log;
        this.check = check;
    }

    public Content current() {
        Content content = current.get();
        if (content == null) {
            reload();
            content = current.get();
        }
        return content;
    }

    /** @return the problems found, each a line for the log and for whoever reloaded */
    public synchronized List<String> reload() {
        if (!store.exists()) {
            store.writeTextIfAbsent(shipped());
        }
        YamlConfiguration yaml = store.readIfReadable().orElseGet(() -> {
            log.warn("content.yml could not be read; the islands run on the shipped content until it can.");
            return YamlConfiguration.loadConfiguration(new java.io.StringReader(shipped()));
        });
        if (beforePhases(yaml)) {
            yaml = replaceOld(yaml);
        }
        ContentReader.Result result = ContentReader.read(yaml, check);
        current.set(result.content());
        result.problems().forEach(problem -> log.warn("content.yml: {}", problem));
        log.info("Island content: {} / {} / {} phase(s) in the overworld / nether / end, team size {}.",
                result.content().phasesOf(IslandDimension.OVERWORLD).size(),
                result.content().phasesOf(IslandDimension.NETHER).size(),
                result.content().phasesOf(IslandDimension.END).size(), result.content().teamSize());
        return result.problems();
    }

    /** 0.4.0's pools, upgrades and shards, without phases: read as it is, every dimension would be one plain block. */
    static boolean beforePhases(YamlConfiguration yaml) {
        return !yaml.contains("phases") && (yaml.contains("pools") || yaml.contains("upgrades") || yaml.contains("shards"));
    }

    /** Keeps the old file beside it, then plays the shipped phases. Without the backup written, nothing is replaced. */
    private YamlConfiguration replaceOld(YamlConfiguration old) {
        YamlStore backup = new YamlStore(file.resolveSibling(BACKUP));
        String text = store.readText().orElse(null);
        boolean kept = text != null && (backup.writeTextIfAbsent(text) || backup.exists());
        if (!kept || !store.writeText(shipped())) {
            log.warn("content.yml is from before phases and could not be replaced; the islands play one plain block "
                    + "per dimension until it is. Replace it by hand with the shipped one.");
            return old;
        }
        log.warn("content.yml was from before phases (pools, upgrades, shards). It is kept as {} and the shipped "
                + "phases are in place now.", BACKUP);
        return YamlConfiguration.loadConfiguration(new java.io.StringReader(shipped()));
    }

    static String shipped() {
        try (InputStream in = OneBlockModule.class.getResourceAsStream("content.yml")) {
            if (in == null) {
                throw new IllegalStateException("content.yml is missing from the jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new IllegalStateException("content.yml could not be read from the jar", unreadable);
        }
    }
}
