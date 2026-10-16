package com.click4bonds.app.Modules.Storage.Service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.click4bonds.app.Modules.Storage.Config.StorageProperties;
import com.click4bonds.app.Modules.Storage.Exception.UploadRejectedException;
import com.click4bonds.app.Modules.Storage.Model.ObjectMetadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The application's way of accepting a file from a client.
 *
 * <h2>What this is for</h2>
 *
 * <p>Bytes that arrive from outside <em>this application</em> have to be
 * handled differently from bytes it produced itself, and this is where that
 * difference lives. A generated letter is written by code this repository
 * controls, to a key it derives, in a format it chose — nothing about it can be
 * hostile. A customer's scanned document is none of those things.</p>
 *
 * <p>So this class is the policy layer over
 * {@link com.click4bonds.app.Modules.Storage.Service.ObjectStore}, which stays as
 * it was: a store that writes what it is told. The split is deliberate, and it
 * runs the other way from what a single façade would do. Putting the upload
 * rules inside the store would apply a client-facing allow-list to the
 * application's own generated documents, so a spreadsheet the deal module
 * produces would start being refused by a rule written for a customer's scan.
 * Two layers, because there are two kinds of bytes.</p>
 *
 * <h2>The flow</h2>
 *
 * <ol>
 *   <li>{@link #presignUpload} — the client says what it wants to send. The
 *       type is checked against the allow-list and the key is derived here,
 *       never taken from the request, so the client has no say in where its
 *       bytes land. A signed URL is returned.</li>
 *   <li>The client {@code PUT}s the file straight to the bucket. It does not
 *       pass through this application, which is the point: a 20 MB scan does
 *       not occupy a request thread or a heap buffer here.</li>
 *   <li>{@link #confirmUpload} — the client reports that it has finished, and
 *       the object is read back and checked. This step is not optional: it is
 *       the only place the size limit can be applied, and until it runs nothing
 *       has verified that what landed is what was authorised.</li>
 * </ol>
 *
 * <h2>What is trusted where</h2>
 *
 * <p>The key and the content type are decided by this application and signed
 * into the URL, so the client cannot vary either — the provider enforces the
 * type, and the key is not the client's to choose. The <em>size</em> is the one
 * thing a client genuinely controls, because a signed {@code PUT} cannot bind
 * the length of its body; it is therefore the one thing checked by reading the
 * result rather than by constraining the request. {@link #confirmUpload}
 * removes an object that fails, so a refused upload leaves nothing behind.</p>
 *
 * <p>This class does not re-expose reading or deleting. Those are
 * {@code ObjectStore}'s, and a caller that needs them — to serve the file back,
 * or to delete the record when a business record is removed — injects the store,
 * as {@code R2DocumentStorage} already does. A pass-through method for each
 * would make this a façade with no policy in it for most of its surface.</p>
 *
 * @see com.click4bonds.app.Modules.Storage.Config.StorageProperties.R2
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadService {

    /**
     * A prefix is a path, and every segment of it must be one this module would
     * have written itself. Anything else — a leading slash, a parent segment, a
     * character that means something to a filesystem or a URL — is a caller
     * constructing a path rather than naming a folder.
     */
    private static final Pattern SAFE_PREFIX =
            Pattern.compile("[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*");

    /**
     * The extensions objects are stored under, by content type.
     *
     * <p>Covers the default allow-list plus the handful of types a deployment is
     * most likely to add to it. A type that is allowed but absent here is
     * refused when a key would be built for it, naming the property.</p>
     */
    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("application/pdf", ".pdf"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
            Map.entry("application/msword", ".doc"),
            Map.entry("application/vnd.ms-excel", ".xls"),
            Map.entry("text/csv", ".csv"),
            Map.entry("text/plain", ".txt"),
            Map.entry("image/png", ".png"),
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/gif", ".gif"),
            Map.entry("image/webp", ".webp"));

    /** Filed by the month a file arrived, mirroring how documents are keyed. */
    private static final DateTimeFormatter MONTH_PREFIX =
            DateTimeFormatter.ofPattern("yyyy/MM");

    private final ObjectStore objectStore;

    private final StorageProperties properties;

    /**
     * An upload the client has been authorised to perform.
     *
     * @param key         where the object will be stored. Derived here, and the
     *                    client has no influence over it — it is returned so
     *                    the caller can record it before the upload happens
     * @param url         the signed URL to {@code PUT} the file to
     * @param contentType the type the upload is authorised as. <strong>The
     *                    client must send this as the {@code Content-Type}
     *                    header</strong>: it is signed into the URL, so any
     *                    other value is rejected by the bucket
     * @param expiresAt   when the URL stops working
     * @param maxSize     the largest body that will be accepted, in bytes. Sent
     *                    so a client can refuse an oversized file before
     *                    spending a slow connection on it; the application
     *                    enforces it regardless
     */
    public record PresignedUpload(
            String key,
            URI url,
            String contentType,
            Instant expiresAt,
            long maxSize
    ) {
    }

    /**
     * Temporary read access to a stored object.
     *
     * @param key       the object the URL grants access to
     * @param url       the signed URL
     * @param expiresAt when it stops working
     */
    public record PresignedDownload(
            String key,
            URI url,
            Instant expiresAt
    ) {
    }

    /**
     * Authorises one upload and returns the URL for it.
     *
     * <p>The content type is checked against the configured allow-list before
     * anything is signed, so a refused type never reaches the bucket, and the
     * key is derived rather than accepted — which is what stops a client
     * choosing a key that overwrites something else, and what makes the result
     * of an upload unguessable from outside.</p>
     *
     * @param prefix     where in the bucket the file belongs, e.g. {@code kyc}.
     *                   Chosen by the calling module, not by a request; a value
     *                   that is not a plain relative path is a programming
     *                   error and fails loudly
     * @param contentType the type the client intends to send. Anything after a
     *                   {@code ;} is ignored, so {@code text/plain; charset=utf-8}
     *                   is judged as {@code text/plain}
     * @return the URL to upload to, and the key it will land under
     * @throws UploadRejectedException when the type is not allowed, or no file
     *                                 extension is known for it
     * @throws com.click4bonds.app.Modules.Storage.Exception.StorageException
     *         when the URL could not be signed, in practice because the store is
     *         not configured
     */
    public PresignedUpload presignUpload(String prefix, String contentType) {

        StorageProperties.R2 config = properties.getR2();

        String safePrefix = requireSafePrefix(prefix);

        String normalized = normalizeContentType(contentType);

        requireAllowedContentType(normalized);

        String key = deriveKey(safePrefix, normalized);

        Duration ttl = config.getPresignedUploadTtl();

        URI url = objectStore.presignedPutUrl(key, normalized, ttl);

        log.info(
                "Presigned upload issued: key={} contentType={} ttl={} maxSize={}",
                key,
                normalized,
                ttl,
                config.getMaxUploadSize().toBytes());

        return new PresignedUpload(
                key,
                url,
                normalized,
                Instant.now().plus(ttl),
                config.getMaxUploadSize().toBytes());
    }

    /**
     * Hands out temporary read access to an object.
     *
     * <p>Kept here beside the upload half because the two are used together —
     * a caller that has just accepted a file usually wants to show it back —
     * and because the lifetime is a property of this module's policy rather
     * than of the object.</p>
     *
     * @param key key of the object to grant access to
     * @return the URL to read it from, until it expires
     * @throws com.click4bonds.app.Modules.Storage.Exception.StorageException
     *         when the key is unusable or the URL could not be signed
     */
    public PresignedDownload presignDownload(String key) {

        Duration ttl = properties.getR2().getPresignedDownloadTtl();

        URI url = objectStore.presignedGetUrl(key, ttl);

        return new PresignedDownload(key, url, Instant.now().plus(ttl));
    }

    /**
     * Checks what actually landed under a key, and removes it if it should not
     * have.
     *
     * <p>The step that makes the size limit real. What the client claimed
     * before uploading is not evidence; this reads the object's own metadata —
     * the provider's record of what it accepted — and compares it against the
     * type that was authorised and the size that is allowed.</p>
     *
     * <p>An object that fails is <strong>deleted before the exception is
     * thrown</strong>. Leaving it would mean the bucket accumulates every
     * oversized file a client ever sent while the application reports that it
     * accepted none, and — worse — the object would still be readable by
     * anyone holding its URL. Deleting it first means a rejection leaves the
     * bucket as it was.</p>
     *
     * <p>Failure to delete is logged and does not replace the rejection: the
     * caller still needs to be told their upload was refused, and telling them
     * "the cleanup also failed" would bury the reason they can act on.</p>
     *
     * @param key                 the key from
     *                            {@link #presignUpload} — the client reports
     *                            this back, which is safe because a wrong key
     *                            only fails to find an object
     * @param expectedContentType the type the upload was authorised as, also
     *                            from {@link #presignUpload}. Checked against
     *                            what the provider stored rather than assumed:
     *                            the two should always agree, and a
     *                            disagreement means something other than this
     *                            flow wrote the object
     * @return the object's metadata, as the provider reports it, for the caller
     *         to record
     * @throws UploadRejectedException when nothing was uploaded under the key,
     *                                 or what was uploaded is not what was
     *                                 authorised
     * @throws com.click4bonds.app.Modules.Storage.Exception.StorageException
     *         when the store could not be read
     */
    public ObjectMetadata confirmUpload(String key, String expectedContentType) {

        String expected = normalizeContentType(expectedContentType);

        Optional<ObjectMetadata> stored = objectStore.stat(key);

        if (stored.isEmpty()) {

            /*
             * The client reports the key back, so this is an ordinary outcome
             * rather than an attack: a connection dropped, a browser closed, a
             * file that failed to read. Worded as the client's problem because
             * it is not the application's — nothing arrives here until the
             * client says it has finished.
             */
            throw new UploadRejectedException(
                    "No upload was found under key " + key
                            + ". Send the file to the upload URL before confirming it.");
        }

        ObjectMetadata metadata = stored.get();

        String actual = normalizeContentType(metadata.contentType());

        try {

            if (!actual.equals(expected)) {

                throw new UploadRejectedException(
                        "The upload under key " + key + " is " + actual
                                + ", not the " + expected + " it was authorised as.");
            }

            /*
             * Re-checked rather than assumed to have been allowed at presign
             * time: configuration can change between the two calls, and an
             * object stored under a type that is no longer permitted should not
             * survive on the strength of having been permitted once.
             */
            requireAllowedContentType(actual);

            requireWithinSizeLimit(metadata, key);

        } catch (UploadRejectedException rejected) {

            discard(key, rejected);

            throw rejected;
        }

        log.info(
                "Upload confirmed: key={} contentType={} size={}",
                key,
                actual,
                metadata.size());

        return metadata;
    }

    /**
     * Removes an object that arrived under a URL this module signed.
     *
     * <p>Only called from a rejection path today. Kept as a method because the
     * deletion and the reason for it are one decision, and because the logging
     * that surrounds it — which failure must not be allowed to mask which — is
     * the part that is easy to get wrong inline.</p>
     */
    private void discard(String key, UploadRejectedException rejected) {

        try {

            objectStore.delete(key);

            log.warn(
                    "Removed a rejected upload: key={} reason={}",
                    key,
                    rejected.getMessage());

        } catch (RuntimeException cleanupFailure) {

            /*
             * Logged with the rejection rather than thrown in its place. The
             * caller asked why their upload was refused, and an object left in
             * the bucket is an operator's problem — reported in the log, not
             * instead of the answer.
             */
            log.error(
                    "A rejected upload could not be removed and is still in the bucket: key={}",
                    key,
                    cleanupFailure);
        }
    }

    /**
     * The key an upload lands under, derived and never supplied.
     *
     * <p>Two properties are wanted at once and both come from the same place.
     * The name is unguessable, so one client cannot name — and therefore cannot
     * read over, or overwrite — another's object. And it is deterministic in
     * shape, grouped by the month it arrived, so an operator looking for what
     * was uploaded in a given month has something to list.</p>
     *
     * <p>Nothing from the client's own filename appears in it. Those are
     * chosen by whoever is uploading, they may contain any character, and their
     * only effect here would be to make the key partly somebody else's to
     * decide — which is exactly what deriving it is meant to avoid. A caller
     * that wants the name a person sees records it itself; it is not storage's
     * business.</p>
     */
    private String deriveKey(String prefix, String contentType) {

        return prefix
                + "/"
                + LocalDate.now().format(MONTH_PREFIX)
                + "/"
                + UUID.randomUUID()
                + fileExtensionFor(contentType);
    }

    /**
     * The extension to file an object under, resolved from its content type.
     *
     * <p>The name a stored object carries is derived from its declared type
     * rather than from anything the client sent, and this is why the allow-list
     * and the key layout are one decision rather than two: a type has to be
     * nameable before it can be stored, so a type this module cannot name is
     * refused rather than stored extension-less and unopenable.</p>
     *
     * <p>Written out rather than looked up. Spring resolves a filename to a
     * content type but not the reverse — {@code MediaTypeFactory} has no
     * {@code getFileExtension} — and the set that matters here is the allow-list,
     * which is short and is not going to be extended with something exotic. A
     * type that is allowed but unnameable is refused at the point a key would be
     * built for it, naming the property, so the mistake surfaces where it was
     * made rather than as an object nobody can open.</p>
     */
    private static String fileExtensionFor(String contentType) {

        String extension = EXTENSIONS.get(contentType);

        if (extension == null) {

            throw new UploadRejectedException(
                    "No file extension is known for " + contentType
                            + ", so an object key cannot be built for it. Remove it from "
                            + "storage.r2.allowed-content-types, or teach UploadService.EXTENSIONS "
                            + "the extension it should be stored under.");
        }

        return extension;
    }

    /**
     * Reduces a declared content type to the form the allow-list is written in.
     *
     * <p>Parameters are dropped rather than compared, because a client that
     * sends {@code text/plain; charset=utf-8} means the same format as one that
     * sends {@code text/plain}, and refusing the first would be a rule about
     * spelling rather than about file types. Case is folded for the same reason:
     * MIME types are case-insensitive, and an allow-list that has to anticipate
     * how a client capitalises them is one that will be got wrong.</p>
     */
    static String normalizeContentType(String contentType) {

        if (!StringUtils.hasText(contentType)) {

            throw new UploadRejectedException(
                    "A content type is required to upload a file.");
        }

        String value = contentType;

        int parameters = value.indexOf(';');

        if (parameters >= 0) {
            value = value.substring(0, parameters);
        }

        value = value.trim().toLowerCase(Locale.ROOT);

        if (value.isEmpty()) {

            throw new UploadRejectedException(
                    "A content type is required to upload a file.");
        }

        return value;
    }

    /**
     * @throws IllegalArgumentException — not {@code UploadRejectedException},
     *         because the prefix comes from calling code rather than from a
     *         request. A bad one is a mistake in this repository, and reporting
     *         it to a client as their fault would send them looking for a
     *         problem in a value they never supplied
     */
    private String requireSafePrefix(String prefix) {

        if (!StringUtils.hasText(prefix)) {
            throw new IllegalArgumentException("An upload prefix is required");
        }

        if (prefix.startsWith("/") || prefix.endsWith("/")) {
            throw new IllegalArgumentException(
                    "An upload prefix must be relative and must not end in a slash: " + prefix);
        }

        if (!SAFE_PREFIX.matcher(prefix).matches()) {

            /*
             * Catches a leading or trailing separator, an empty segment, a
             * backslash, and anything outside the character set — all of which
             * mean the caller is building a path.
             */
            throw new IllegalArgumentException(
                    "An upload prefix must be a path of [A-Za-z0-9._-] segments: " + prefix);
        }

        for (String segment : prefix.split("/")) {

            /*
             * "." and ".." are inside the character set above and survive the
             * match, and both mean a directory rather than a folder name: a
             * prefix of ".." would put every object a level above where the
             * caller thought, and "kyc/../admin" would file it under somebody
             * else's folder. Rejected outright rather than rewritten, because
             * rewriting would silently file the object somewhere the caller did
             * not name.
             *
             * The store refuses a key with a parent segment too, so this is
             * defence in depth rather than the only guard. It belongs here as
             * well: a prefix is composed into a key, and the check that the
             * caller's own input was sane is not the store's to make.
             */
            if (segment.equals(".") || segment.equals("..")) {

                throw new IllegalArgumentException(
                        "An upload prefix must not contain a current or parent segment: " + prefix);
            }
        }

        return prefix;
    }

    /**
     * @throws UploadRejectedException naming the type and the property, so a
     *         client that has hit the rule and an operator being asked about it
     *         are both told the same thing
     */
    private void requireAllowedContentType(String contentType) {

        if (properties.getR2().getAllowedContentTypes().contains(contentType)) {
            return;
        }

        throw new UploadRejectedException(
                "Files of type " + contentType + " cannot be uploaded. Allowed types are "
                        + String.join(", ", properties.getR2().getAllowedContentTypes()) + ".");
    }

    /**
     * @throws UploadRejectedException quoting both sizes, because a client that
     *         was within a megabyte of the limit needs to know how far over it
     *         went to decide whether to shrink the file or give up
     */
    private void requireWithinSizeLimit(ObjectMetadata metadata, String key) {

        long limit = properties.getR2().getMaxUploadSize().toBytes();

        if (metadata.size() <= limit) {
            return;
        }

        throw new UploadRejectedException(
                "The upload under key " + key + " is " + metadata.size()
                        + " bytes, over the " + limit + " byte limit.");
    }
}
