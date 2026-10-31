package com.unkittered.api.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaMaintenanceTest {

    @TempDir
    Path dir;

    JdbcTemplate jdbc;
    LocalStorageService storage;
    MediaMaintenance maintenance;

    @BeforeEach
    void setUp() throws Exception {
        storage = new LocalStorageService(dir.toString(), "");
        storage.init();
        Files.createDirectories(storage.privateRoot());
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("table_name", "profiles", "column_name", "image_url")));
        maintenance = new MediaMaintenance(jdbc, storage, 48);
    }

    private Path file(Path in, String name, Duration age) throws Exception {
        Path f = in.resolve(name);
        Files.writeString(f, "x");
        Files.setLastModifiedTime(f, FileTime.from(Instant.now().minus(age)));
        return f;
    }

    private void referenced(String... urls) {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of(urls));
    }

    @Test
    void deletesOnlyOldUnreferencedFiles() throws Exception {
        String kept = "a".repeat(32) + ".jpg";
        String orphan = "b".repeat(32) + ".jpg";
        String fresh = "c".repeat(32) + ".jpg";
        String privateOrphan = "d".repeat(32) + ".pdf";
        Path keptFile = file(dir, kept, Duration.ofDays(10));
        Path orphanFile = file(dir, orphan, Duration.ofDays(10));
        Path freshFile = file(dir, fresh, Duration.ofHours(2));
        Path privateFile = file(storage.privateRoot(), privateOrphan, Duration.ofDays(3));
        Path notOurs = file(dir, "README.txt", Duration.ofDays(10));
        referenced("https://x/files/" + kept);

        assertThat(maintenance.sweep()).isEqualTo(2);

        assertThat(keptFile).exists();
        assertThat(freshFile).exists();      // inside the grace period
        assertThat(notOurs).exists();        // not a name we generate
        assertThat(orphanFile).doesNotExist();
        assertThat(privateFile).doesNotExist();
    }

    @Test
    void refusesToDeleteMostOfALargeStore() throws Exception {
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            files.add(file(dir, String.format("%032x", i) + ".jpg", Duration.ofDays(10)));
        }
        referenced();   // as if the database came back empty

        assertThat(maintenance.sweep()).isZero();
        assertThat(files).allMatch(Files::exists);
    }

    @Test
    void privateFilesRoundTripAndRejectOddNames() {
        String ref = storage.storePrivate(new byte[] {1, 2, 3}, "application/pdf", "proof.pdf");
        assertThat(ref).startsWith("/private/").endsWith(".pdf");
        String name = ref.substring("/private/".length());

        var read = storage.readPrivate(name);
        assertThat(read).isPresent();
        assertThat(read.get().contentType()).isEqualTo("application/pdf");
        assertThat(read.get().bytes()).containsExactly(1, 2, 3);

        assertThat(storage.readPrivate("../" + name)).isEmpty();
        assertThat(storage.readPrivate("missing.pdf")).isEmpty();
    }
}
