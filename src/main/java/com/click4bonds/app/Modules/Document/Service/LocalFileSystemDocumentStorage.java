package com.click4bonds.app.Modules.Document.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Exception.DocumentStorageException;
import com.click4bonds.app.Modules.Document.Model.StoredDocument;

import lombok.extern.slf4j.Slf4j;

/**
 * Stores documents as files under a configured root directory.
 *
 * <p>The root is resolved once, at construction, so a relative
 * {@code document.storage.directory} means the same directory for the life of
 * the process even if the working directory later changes.</p>
 *
 * <p><strong>Nothing is created until the first store.</strong> The directory is
 * not made in the constructor: a context-load test, or a deployment where
 * documents are switched off, must not fail because a path is unwritable, and
 * creating directories as a side effect of starting up hides exactly the
 * misconfiguration an operator needs to see.</p>
 */
@Service
@Slf4j
public class LocalFileSystemDocumentStorage implements DocumentStorage {

    private final Path root;

    public LocalFileSystemDocumentStorage(DocumentProperties properties) {

        this.root = Paths.get(properties.getStorage().getDirectory())
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public StoredDocument store(
            String storageKey,
            String fileName,
            String contentType,
            byte[] content) {

        Path target = resolve(storageKey);

        try {

            Files.createDirectories(target.getParent());

            /*
             * Written to a sibling and then moved into place. A crash or a full
             * disk mid-write would otherwise leave a truncated file at the real
             * key, and a truncated file is worse than a missing one: it looks
             * like a document and reports its size as if it were complete.
             */
            Path partial = target.resolveSibling(target.getFileName() + ".part");

            Files.write(partial, content);

            moveIntoPlace(partial, target);

            log.info(
                    "Document stored: key={} bytes={} contentType={}",
                    storageKey,
                    content.length,
                    contentType);

            return new StoredDocument(storageKey, fileName, contentType, content.length);

        } catch (IOException failure) {

            throw new DocumentStorageException(
                    "Failed to store document at key " + storageKey, failure);
        }
    }

    @Override
    public byte[] read(String storageKey) {

        Path target = resolve(storageKey);

        try {

            return Files.readAllBytes(target);

        } catch (IOException failure) {

            throw new DocumentStorageException(
                    "Failed to read document at key " + storageKey, failure);
        }
    }

    @Override
    public boolean exists(String storageKey) {

        return Files.isRegularFile(resolve(storageKey));
    }

    /**
     * Turns a relative key into an absolute path, refusing anything that would
     * land outside the storage root.
     */
    private Path resolve(String storageKey) {

        if (storageKey == null || storageKey.isBlank()) {
            throw new DocumentStorageException("Document key is required");
        }

        Path candidate = Paths.get(storageKey);

        /*
         * An absolute key would silently win over the root — Path.resolve
         * returns the absolute argument unchanged — and would write wherever it
         * pointed. Rejected outright rather than quietly re-rooted.
         */
        if (candidate.isAbsolute()) {
            throw new DocumentStorageException(
                    "Document key must be relative to the storage root: " + storageKey);
        }

        Path resolved = root.resolve(candidate).normalize();

        if (!resolved.startsWith(root)) {
            throw new DocumentStorageException(
                    "Document key escapes the storage root: " + storageKey);
        }

        return resolved;
    }

    /**
     * Moves the finished file onto its real key.
     *
     * <p>An atomic move is preferred so a reader never observes a half-written
     * file. Where the platform cannot promise one — some network filesystems —
     * a plain replace is used instead: the file is already fully written, so the
     * only thing lost is the guarantee, not the content.</p>
     */
    private void moveIntoPlace(Path partial, Path target) throws IOException {

        try {

            Files.move(
                    partial,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

        } catch (AtomicMoveNotSupportedException notAtomic) {

            log.debug(
                    "Atomic move unavailable for {}; falling back to replace",
                    target,
                    notAtomic);

            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
