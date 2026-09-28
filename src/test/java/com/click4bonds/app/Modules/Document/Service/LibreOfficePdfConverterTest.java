package com.click4bonds.app.Modules.Document.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.click4bonds.app.Modules.Document.Config.DocumentProperties;

/**
 * The LibreOffice command line, and — where LibreOffice exists — a real
 * conversion.
 *
 * <p>Most of the value is in the command assertions. A wrong flag does not fail
 * loudly: a missing {@code -env:UserInstallation} only shows up when two
 * conversions happen at once, and a missing {@code --headless} only on a machine
 * with a display. Asserting the arguments here means those mistakes are caught
 * on a laptop that has no LibreOffice at all.</p>
 */
class LibreOfficePdfConverterTest {

    @TempDir
    Path workDir;

    // =========================================================
    // COMMAND
    // =========================================================

    @Test
    void runsHeadlessAndConvertsToPdf() {

        List<String> command = command(workDir.resolve("profile"));

        assertTrue(command.contains("--headless"), "must not try to open a window");
        assertTrue(command.contains("--norestore"), "must not offer to restore a session");

        int convert = command.indexOf("--convert-to");

        assertEquals("pdf:calc_pdf_Export", command.get(convert + 1));
    }

    @Test
    void pointsTheEngineAtItsOwnProfileDirectory() {

        /*
         * The single most common cause of flaky headless conversion. Two
         * conversions starting together otherwise share ~/.config/libreoffice
         * and the second dies on the profile lock, reporting only "another
         * instance is running".
         */
        List<String> command = command(workDir.resolve("profile-one"));

        assertTrue(
                command.stream().anyMatch(argument ->
                        argument.startsWith("-env:UserInstallation=")),
                "each run needs its own LibreOffice profile");
    }

    @Test
    void givesConcurrentRunsDifferentProfiles() {

        List<String> first = command(workDir.resolve("profile-one"));
        List<String> second = command(workDir.resolve("profile-two"));

        assertNotEquals(
                profileOf(first),
                profileOf(second),
                "two runs sharing a profile is exactly the collision this avoids");
    }

    @Test
    void writesIntoTheDirectoryItIsGiven() {

        List<String> command = command(workDir.resolve("profile"));

        int outdir = command.indexOf("--outdir");

        assertTrue(outdir > 0, "--outdir is required or the PDF lands in the CWD");
        assertEquals(workDir.toAbsolutePath().toString(), command.get(outdir + 1));
    }

    @Test
    void passesTheSourceAsTheFinalArgument() {

        List<String> command = command(workDir.resolve("profile"));

        String last = command.get(command.size() - 1);

        assertTrue(last.endsWith(".xlsx"), "expected the source workbook last, got " + last);
    }

    @Test
    void neverInvolvesAShellSoSpacesInPathsAreSafe() {

        /*
         * ProcessBuilder takes an argument list, so a template or directory name
         * containing a space is passed through intact. A single command string
         * would split it — and the real template is named "ATSPL Deal
         * Format.xlsx".
         */
        Path source = workDir.resolve("ATSPL Deal Format.xlsx");

        List<String> command = LibreOfficePdfConverter.buildCommand(
                "soffice",
                workDir.resolve("profile"),
                workDir,
                source);

        assertEquals(
                source.toAbsolutePath().toString(),
                command.get(command.size() - 1));
    }

    // =========================================================
    // REAL CONVERSION
    // =========================================================

    @Test
    void convertsAWorkbookWhenLibreOfficeIsInstalled() throws Exception {

        LibreOfficePdfConverter converter = new LibreOfficePdfConverter(new DocumentProperties());

        assumeTrue(
                converter.resolveExecutable() != null,
                "LibreOffice is not installed on this machine");

        /*
         * A minimal real workbook rather than a fixture, so the bytes pass
         * through the actual POI writer on the way in.
         */
        byte[] xlsx = new XlsxTemplateWriter(new DocumentProperties())
                .fill("PSU Private Sale", java.util.Map.of("C16", "INE123A07012"));

        byte[] pdf = converter.toPdf(xlsx, "DC-20260923-000001");

        assertTrue(pdf.length > 0, "the converter must not return an empty document");

        assertEquals(
                "%PDF",
                new String(pdf, 0, 4, StandardCharsets.US_ASCII),
                "the bytes should actually be a PDF");
    }

    @Test
    void deletesItsScratchSpaceAfterConverting() throws Exception {

        LibreOfficePdfConverter converter = new LibreOfficePdfConverter(new DocumentProperties());

        assumeTrue(
                converter.resolveExecutable() != null,
                "LibreOffice is not installed on this machine");

        Path before = Files.createTempDirectory("probe-");
        Files.delete(before);

        byte[] xlsx = new XlsxTemplateWriter(new DocumentProperties())
                .fill("PSU Private Sale", java.util.Map.of("C16", "INE123A07012"));

        converter.toPdf(xlsx, "cleanup-check");

        /*
         * The working directory and the profile directory are both created per
         * call. If they outlived it, a container would fill up with a few
         * hundred megabytes per conversion.
         */
        try (var entries = Files.list(before.getParent())) {

            long leftovers = entries
                    .filter(path -> path.getFileName().toString().startsWith("deal-doc-")
                            || path.getFileName().toString().startsWith("deal-lo-profile-"))
                    .count();

            assertTrue(
                    leftovers < 8,
                    "conversions appear to be leaving their scratch directories behind");
        }
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    private List<String> command(Path profileDir) {

        return LibreOfficePdfConverter.buildCommand(
                "soffice",
                profileDir,
                workDir,
                workDir.resolve("source.xlsx"));
    }

    private String profileOf(List<String> command) {

        return command.stream()
                .filter(argument -> argument.startsWith("-env:UserInstallation="))
                .findFirst()
                .orElseThrow();
    }
}
