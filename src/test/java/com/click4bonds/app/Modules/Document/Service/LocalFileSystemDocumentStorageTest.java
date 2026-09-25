package com.click4bonds.app.Modules.Document.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Exception.DocumentStorageException;
import com.click4bonds.app.Modules.Document.Model.DocumentFormat;
import com.click4bonds.app.Modules.Document.Model.StoredDocument;

/**
 * Filesystem storage: the key layout, and the two guarantees a caller depends
 * on — that a key cannot escape the storage root, and that re-storing a key
 * replaces rather than accumulates.
 */
class LocalFileSystemDocumentStorageTest {

    @TempDir
    Path root;

    @Test
    void storesAndReadsBackUnderTheConfiguredRoot() {

        LocalFileSystemDocumentStorage storage = storageAt(root);

        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        StoredDocument stored = storage.store(
                "2026/09/DC-20260923-000001.pdf",
                "DC-20260923-000001.pdf",
                DocumentFormat.PDF.contentType(),
                content);

        assertEquals("2026/09/DC-20260923-000001.pdf", stored.storageKey());
        assertEquals(content.length, stored.size());

        assertTrue(Files.isRegularFile(root.resolve("2026/09/DC-20260923-000001.pdf")));
        assertTrue(storage.exists("2026/09/DC-20260923-000001.pdf"));

        assertEquals("hello", new String(
                storage.read("2026/09/DC-20260923-000001.pdf"),
                StandardCharsets.UTF_8));
    }

    @Test
    void createsTheIntermediateDirectoriesTheKeyImplies() {

        LocalFileSystemDocumentStorage storage = storageAt(root);

        storage.store("2026/09/deep.pdf", "deep.pdf", "application/pdf", bytes());

        assertTrue(Files.isDirectory(root.resolve("2026/09")));
    }

    @Test
    void replacesAnExistingDocumentRatherThanAccumulating() {

        LocalFileSystemDocumentStorage storage = storageAt(root);

        storage.store("2026/09/deal.pdf", "deal.pdf", "application/pdf", bytes());

        /*
         * Regenerating a letter after a failure has to land on the same key.
         * If it appended instead, every retry would leave another orphaned file
         * behind and the deal row would point at whichever was written last.
         */
        storage.store(
                "2026/09/deal.pdf",
                "deal.pdf",
                "application/pdf",
                "second".getBytes(StandardCharsets.UTF_8));

        assertEquals(
                "second",
                new String(storage.read("2026/09/deal.pdf"), StandardCharsets.UTF_8));
    }

    @Test
    void leavesNoPartialFileBehindAfterASuccessfulStore() throws IOException {

        LocalFileSystemDocumentStorage storage = storageAt(root);

        storage.store("2026/09/deal.pdf", "deal.pdf", "application/pdf", bytes());

        try (var entries = Files.walk(root)) {

            assertTrue(
                    entries.noneMatch(path -> path.toString().endsWith(".part")),
                    "the temporary file should have been moved, not left behind");
        }
    }

    // =========================================================
    // CONTAINMENT
    // =========================================================

    @Test
    void refusesAKeyThatClimbsOutOfTheStorageRoot() {

        LocalFileSystemDocumentStorage storage = storageAt(root);

        /*
         * The key reaches a filesystem write, so a traversal has to be refused
         * rather than sanitised into something that silently points elsewhere.
         */
        DocumentStorageException failure = assertThrows(
                DocumentStorageException.class,
                () -> storage.store(
                        "../../etc/passwd",
                        "passwd",
                        "text/plain",
                        bytes()));

        assertTrue(failure.getMessage().contains("escapes"));
    }

    @Test
    void refusesAnAbsoluteKey() {

        LocalFileSystemDocumentStorage storage = storageAt(root);

        /*
         * Path.resolve returns an absolute argument unchanged, so an absolute
         * key would silently win over the root and write wherever it pointed.
         */
        DocumentStorageException failure = assertThrows(
                DocumentStorageException.class,
                () -> storage.store(
                        root.resolve("elsewhere.pdf").toAbsolutePath().toString(),
                        "elsewhere.pdf",
                        "application/pdf",
                        bytes()));

        assertTrue(failure.getMessage().contains("relative"));
    }

    @Test
    void reportsAReadOfSomethingThatIsNotThere() {

        LocalFileSystemDocumentStorage storage = storageAt(root);

        assertThrows(
                DocumentStorageException.class,
                () -> storage.read("2026/09/missing.pdf"));

        assertFalse(storage.exists("2026/09/missing.pdf"));
    }

    // =========================================================
    // KEYS
    // =========================================================

    @Test
    void filesADocumentUnderTheMonthItBelongsTo() {

        assertEquals(
                "2026/09/DC-20260923-000001.pdf",
                DocumentKeys.forDeal(
                        "DC-20260923-000001",
                        LocalDate.of(2026, 9, 23),
                        DocumentFormat.PDF));

        assertEquals(
                "2026/09/DC-20260923-000001.xlsx",
                DocumentKeys.forDeal(
                        "DC-20260923-000001",
                        LocalDate.of(2026, 9, 23),
                        DocumentFormat.XLSX));
    }

    @Test
    void givesBothFormatsOfOneDocumentTheSameStem() {

        /*
         * The spreadsheet and the PDF of a deal have to sit together under one
         * stem, so a later regeneration replaces a matched pair rather than
         * leaving one format orphaned.
         */
        String stem = DocumentKeys.stemForDeal(
                "DC-20260923-000001",
                LocalDate.of(2026, 9, 23));

        assertEquals(
                stem + ".pdf",
                DocumentKeys.forDeal(
                        "DC-20260923-000001", LocalDate.of(2026, 9, 23), DocumentFormat.PDF));

        assertEquals(
                stem + ".xlsx",
                DocumentKeys.forDeal(
                        "DC-20260923-000001", LocalDate.of(2026, 9, 23), DocumentFormat.XLSX));
    }

    @Test
    void neutralisesSeparatorsInAReferenceSoItCannotAddAPathSegment() {

        /*
         * What makes a traversal possible is a separator, not the dots. Every
         * separator in the reference is replaced, so the reference always
         * contributes exactly one filename and the only separators left in the
         * key are the two the date prefix put there. The ".." that survives is
         * inside a filename, where it means nothing — and the storage layer
         * refuses an escaping key outright regardless.
         */
        String key = DocumentKeys.forDeal(
                "DC/2026/../evil",
                LocalDate.of(2026, 9, 23),
                DocumentFormat.PDF);

        assertEquals("2026/09/DC_2026_.._evil.pdf", key);

        assertEquals(
                2,
                key.chars().filter(character -> character == '/').count(),
                "only the yyyy/MM prefix may contribute separators");
    }

    @Test
    void refusesAReferenceThatIsNothingButADirectory() {

        /*
         * "." and ".." survive the character filter — they are made of legal
         * characters — and both mean a directory rather than a file, so they are
         * rejected rather than rewritten into a name that points elsewhere.
         */
        assertThrows(
                IllegalArgumentException.class,
                () -> DocumentKeys.forDeal(
                        "..",
                        LocalDate.of(2026, 9, 23),
                        DocumentFormat.PDF));
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    private LocalFileSystemDocumentStorage storageAt(Path directory) {

        DocumentProperties properties = new DocumentProperties();

        properties.getStorage().setDirectory(directory.toString());

        return new LocalFileSystemDocumentStorage(properties);
    }

    private byte[] bytes() {
        return "content".getBytes(StandardCharsets.UTF_8);
    }
}
