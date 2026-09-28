package com.click4bonds.app.Modules.Document.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException;

import lombok.extern.slf4j.Slf4j;

/**
 * Renders a spreadsheet to PDF with a headless LibreOffice subprocess.
 *
 * <p>LibreOffice is driven as a separate process rather than embedded because
 * there is no embeddable option: it is a desktop application, and every JVM-side
 * alternative is either a commercial licence or a re-implementation of the
 * spreadsheet layout that would not match the template.</p>
 *
 * <p>Four things about running it headless are easy to get wrong, and all four
 * are handled here because each one fails intermittently rather than obviously:</p>
 *
 * <ul>
 *   <li><strong>A private profile per invocation.</strong> Two conversions
 *       starting together otherwise share {@code ~/.config/libreoffice} and the
 *       second dies on the profile lock, reporting only "another instance is
 *       running". This is the most common cause of flaky headless conversion.</li>
 *   <li><strong>A writable HOME.</strong> LibreOffice writes outside its profile
 *       directory too, and breaks even with a custom one if HOME is read-only.</li>
 *   <li><strong>Output is drained.</strong> An unread pipe fills and deadlocks
 *       the child process, which then looks like a hang.</li>
 *   <li><strong>The exit code is not trusted.</strong> LibreOffice exits 0 in
 *       cases where no document was written, so the output file is checked.</li>
 * </ul>
 */
@Service
@Slf4j
public class LibreOfficePdfConverter implements PdfConverter {

    private final DocumentProperties properties;

    /**
     * Bounds how many conversions run at once. Each one is a separate process
     * taking a few hundred megabytes, and there is no queue in front of this:
     * without a bound, N simultaneous purchases start N processes.
     */
    private final Semaphore permits;

    public LibreOfficePdfConverter(DocumentProperties properties) {

        this.properties = properties;

        int max = Math.max(1, properties.getPdf().getMaxConcurrentConversions());

        this.permits = new Semaphore(max, true);
    }

    /**
     * Reports a missing engine once, at startup, rather than on every purchase.
     *
     * <p>Warns and continues. A machine without LibreOffice is a legitimate
     * configuration — the spreadsheet is still produced, and
     * {@code document.pdf.enabled=false} says so explicitly — so failing to
     * start would be wrong. But an operator who has left rendering on and
     * forgotten the binary should find out now, not from a customer.</p>
     */
    @jakarta.annotation.PostConstruct
    void warnWhenTheEngineIsMissing() {

        if (!properties.getPdf().isEnabled() || !properties.isEnabled()) {
            return;
        }

        if (resolveExecutable() == null) {

            log.warn(
                    "PDF rendering is enabled but \"{}\" was not found on PATH."
                            + " Deal confirmation letters will fail to render and the"
                            + " deals will stay in CREATED. Install LibreOffice, set"
                            + " document.pdf.soffice-path, or set"
                            + " document.pdf.enabled=false to store the spreadsheet only.",
                    properties.getPdf().getSofficePath());
        }
    }

    @Override
    public byte[] toPdf(byte[] xlsx, String baseName) {

        long timeoutMillis = properties.getPdf().getTimeout().toMillis();

        boolean acquired = false;

        try {

            acquired = permits.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS);

            if (!acquired) {
                throw new DocumentGenerationException(
                        "Timed out waiting for a PDF conversion slot after "
                                + timeoutMillis + "ms");
            }

            return convert(xlsx, baseName, timeoutMillis);

        } catch (InterruptedException interrupted) {

            Thread.currentThread().interrupt();

            throw new DocumentGenerationException(
                    "Interrupted while waiting to convert "
                            + baseName + " to PDF", interrupted);

        } finally {

            if (acquired) {
                permits.release();
            }
        }
    }

    private byte[] convert(byte[] xlsx, String baseName, long timeoutMillis) {

        Path workDir = null;
        Path profileDir = null;

        try {

            workDir = Files.createTempDirectory("deal-doc-");
            profileDir = Files.createTempDirectory("deal-lo-profile-");

            /*
             * LibreOffice derives the output name from the input name, and
             * mangles names containing spaces or non-ASCII characters. The
             * stored document keeps its real name; only this intermediate file
             * has to be plain.
             */
            Path source = workDir.resolve(sanitize(baseName) + ".xlsx");
            Path log = workDir.resolve("soffice.log");

            Files.write(source, xlsx);

            List<String> command = buildCommand(
                    properties.getPdf().getSofficePath(),
                    profileDir,
                    workDir,
                    source);

            int exitCode = run(command, workDir, log, timeoutMillis);

            Path output = workDir.resolve(sanitize(baseName) + ".pdf");

            /*
             * The exit code alone is not evidence: LibreOffice reports success
             * in situations where it wrote nothing at all. A zero-length file is
             * treated as a failure too, because storing it would hand the
             * customer a PDF that opens to a blank page.
             */
            if (!Files.isRegularFile(output) || Files.size(output) == 0) {

                throw new DocumentGenerationException(
                        "LibreOffice exited with " + exitCode
                                + " but produced no PDF for " + baseName
                                + describe(log));
            }

            return Files.readAllBytes(output);

        } catch (IOException failure) {

            throw new DocumentGenerationException(
                    "Failed to convert " + baseName + " to PDF", failure);

        } finally {

            deleteRecursively(workDir);
            deleteRecursively(profileDir);
        }
    }

    /**
     * Runs the conversion and returns its exit code.
     *
     * <p>Both streams go to a file rather than a pipe. Nothing reads a pipe here
     * until the process has exited, and a chatty LibreOffice would fill it and
     * block forever — a deadlock that presents as a timeout with no explanation.</p>
     */
    private int run(List<String> command, Path workDir, Path log, long timeoutMillis)
            throws IOException {

        ProcessBuilder builder = new ProcessBuilder(command);

        builder.directory(workDir.toFile());
        builder.redirectOutput(log.toFile());
        builder.redirectErrorStream(true);

        /*
         * A writable HOME is required even though the profile is redirected:
         * LibreOffice writes caches and lock files outside it as well, and
         * fails opaquely when HOME is read-only — which is the default in a
         * hardened container.
         */
        builder.environment().put("HOME", workDir.toAbsolutePath().toString());
        builder.environment().put("TMPDIR", workDir.toAbsolutePath().toString());

        Process process = builder.start();

        try {

            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {

                terminate(process);

                throw new DocumentGenerationException(
                        "LibreOffice timed out after " + timeoutMillis + "ms"
                                + describe(log));
            }

            return process.exitValue();

        } catch (InterruptedException interrupted) {

            terminate(process);

            Thread.currentThread().interrupt();

            throw new DocumentGenerationException(
                    "Interrupted while converting to PDF", interrupted);
        }
    }

    /**
     * Kills the process and waits for it to actually die.
     *
     * <p>The second wait matters: without it the child can outlive the call and
     * hold the profile directory that is about to be deleted.</p>
     */
    private void terminate(Process process) {

        process.destroyForcibly();

        try {

            process.waitFor(10, TimeUnit.SECONDS);

        } catch (InterruptedException interrupted) {

            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builds the LibreOffice command line.
     *
     * <p>Package-visible so a test can assert the flags without a LibreOffice
     * installation — the flags are the part that is easy to get wrong and
     * impossible to notice, since a missing profile redirect only shows up under
     * concurrency.</p>
     */
    static List<String> buildCommand(
            String sofficePath,
            Path profileDir,
            Path workDir,
            Path source) {

        List<String> command = new ArrayList<>();

        command.add(sofficePath);

        command.add("--headless");
        command.add("--norestore");
        command.add("--nolockcheck");
        command.add("--nodefault");
        command.add("--nofirststartwizard");

        /*
         * The private profile. Without it, concurrent conversions collide on the
         * shared profile lock.
         */
        command.add("-env:UserInstallation=" + profileDir.toUri());

        command.add("--convert-to");
        command.add("pdf:calc_pdf_Export");
        command.add("--outdir");
        command.add(workDir.toAbsolutePath().toString());
        command.add(source.toAbsolutePath().toString());

        return command;
    }

    /**
     * Reduces a document name to something LibreOffice will not mangle.
     *
     * <p>Only the intermediate file is affected — the stored document keeps the
     * name the caller asked for.</p>
     */
    private static String sanitize(String baseName) {

        String cleaned = baseName == null
                ? "document"
                : baseName.replaceAll("[^A-Za-z0-9._-]", "_");

        return cleaned.isBlank() ? "document" : cleaned;
    }

    /** Reads the captured output so a failure can say why. */
    private static String describe(Path log) {

        try {

            if (!Files.isRegularFile(log)) {
                return "";
            }

            String output = Files.readString(log).trim();

            return output.isEmpty() ? "" : "; LibreOffice said: " + output;

        } catch (IOException unreadable) {

            return "";
        }
    }

    private static void deleteRecursively(Path directory) {

        if (directory == null) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {

            paths.sorted(Comparator.reverseOrder())
                    .forEach(LibreOfficePdfConverter::deleteQuietly);

        } catch (IOException failure) {

            /*
             * Cleanup is best-effort. A leftover temp directory is untidy but
             * harmless, and failing a successful conversion because its scratch
             * space could not be removed would be a worse trade.
             */
            org.slf4j.LoggerFactory.getLogger(LibreOfficePdfConverter.class)
                    .warn("Could not clean up temp directory {}", directory, failure);
        }
    }

    private static void deleteQuietly(Path path) {

        try {

            Files.deleteIfExists(path);

        } catch (IOException ignored) {

            // Best-effort; see deleteRecursively.
        }
    }

    /**
     * Resolves the configured executable to an absolute path.
     *
     * <p>Exposed for a startup check: a bare command name is resolved from PATH,
     * and on a machine without LibreOffice the failure should surface once rather
     * than on every purchase.</p>
     *
     * @return the resolved path, or null when the executable cannot be found
     */
    public Path resolveExecutable() {

        String configured = properties.getPdf().getSofficePath();

        try {

            Path direct = Paths.get(configured);

            if (direct.isAbsolute()) {
                return Files.isExecutable(direct) ? direct : null;
            }

            String path = System.getenv("PATH");

            if (path == null) {
                return null;
            }

            for (String entry : path.split(java.io.File.pathSeparator)) {

                Path candidate = Paths.get(entry, configured);

                if (Files.isExecutable(candidate)) {
                    return candidate;
                }
            }

            return null;

        } catch (RuntimeException malformed) {

            return null;
        }
    }
}
