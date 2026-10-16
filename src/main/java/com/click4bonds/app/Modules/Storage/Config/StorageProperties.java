package com.click4bonds.app.Modules.Storage.Config;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import lombok.Data;
import lombok.ToString;

/**
 * Configuration of the object store.
 *
 * <pre>
 * storage:
 *   r2:
 *     endpoint: https://&lt;account-id&gt;.r2.cloudflarestorage.com
 *     access-key-id: ...
 *     secret-access-key: ...
 *     bucket: click4bonds-documents
 *     presigned-upload-ttl: 15m
 *     presigned-download-ttl: 5m
 *     max-upload-size: 25MB
 *     allowed-content-types:
 *       - application/pdf
 *       - image/png
 * </pre>
 *
 * <p>Top-level rather than nested under a feature, because the store is shared:
 * deal confirmation letters are simply the first thing that uses it, and a second
 * feature storing, say, KYC documents configures nothing new.</p>
 *
 * <p><strong>The credentials here are secrets</strong>, unlike the values in
 * {@code DocumentProperties}: an access key grants write access to every object
 * in the bucket. They carry no defaults and are supplied from the environment.</p>
 *
 * <p>Every value is optional as far as starting up is concerned. Nothing is
 * validated at construction, so a machine that has switched document generation
 * off — or a test that only loads the context — starts without any R2
 * configuration at all. A missing value is reported by the operation that needed
 * it, naming the property, rather than by the application failing to boot.</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private R2 r2 = new R2();

    /** Cloudflare R2, reached through its S3-compatible API. */
    @Data
    public static class R2 {

        /**
         * Account-scoped S3 endpoint, of the form
         * {@code https://<account-id>.r2.cloudflarestorage.com}.
         *
         * <p>Required to store anything. R2 has no equivalent of the AWS default
         * endpoint, so there is nothing sensible to fall back to.</p>
         */
        private String endpoint = "";

        /**
         * R2 API token access key id.
         *
         * <p>Excluded from {@code toString} along with the secret below. It is
         * not itself sensitive, but it is half of a credential pair and the
         * {@code @Data} this class carries would otherwise print it — into a
         * binding failure, a debugger's view of the bean, or any future
         * {@code log.debug("{}", properties)} written by someone assuming a
         * config object is safe to log.</p>
         */
        @ToString.Exclude
        private String accessKeyId = "";

        /**
         * R2 API token secret.
         *
         * <p>Read together with {@link #accessKeyId}. When both are blank the
         * provider's own credential chain is used instead — environment
         * variables, a shared profile, or an instance role — which is how a
         * deployment that does not want keys in its configuration supplies
         * them.</p>
         *
         * <p><strong>Excluded from {@code toString}.</strong> The generated
         * form would print the secret verbatim, so a single log statement, a
         * binding error, or a test that prints the bean would write a token with
         * write access to the whole bucket. Lombok has no masking option, so the
         * field is omitted rather than redacted; the accessor is unaffected and
         * is how the store still reads it.</p>
         */
        @ToString.Exclude
        private String secretAccessKey = "";

        /**
         * Bucket every object is written to.
         *
         * <p>Required. Objects are addressed by key within it, so a second
         * feature wanting its own bucket needs its own
         * {@link com.click4bonds.app.Modules.Storage.Service.ObjectStore} rather
         * than a second key namespace — keys are the caller's to choose.</p>
         */
        private String bucket = "";

        /**
         * Signing region. R2 is region-agnostic and expects {@code auto}; the
         * option exists because the S3 client will not build without one and
         * because a genuine S3-compatible target would need its own.
         */
        private String region = "auto";

        /**
         * Ceiling on one stored-object operation, including its retries.
         *
         * <p>An unreachable endpoint must not hold an HTTP request thread open
         * indefinitely. The download endpoint reads through this store, so
         * without a bound a sick bucket becomes a hung customer request.</p>
         */
        private Duration apiCallTimeout = Duration.ofSeconds(30);

        /**
         * Ceiling on a single attempt, before the client's own retries.
         *
         * <p>Shorter than {@link #apiCallTimeout} so a request that is going to
         * fail has room to be retried within the overall budget rather than
         * exhausting it on one attempt.</p>
         */
        private Duration apiCallAttemptTimeout = Duration.ofSeconds(10);

        /**
         * How long a presigned upload URL stays valid.
         *
         * <p>Short by default, because it is handed to a browser and anyone
         * holding it can write to the bucket until it expires. Long enough to
         * cover choosing a file and sending it; not long enough to be worth
         * stealing.</p>
         */
        private Duration presignedUploadTtl = Duration.ofMinutes(15);

        /**
         * How long a presigned download URL stays valid.
         *
         * <p>Shorter than the upload lifetime on purpose. An upload has to
         * survive a person finding a file and a slow connection; a download
         * starts the moment the URL is returned, and the URL is the only thing
         * standing between the public and the object.</p>
         */
        private Duration presignedDownloadTtl = Duration.ofMinutes(5);

        /**
         * Largest object a client may upload directly.
         *
         * <p><strong>Enforced after the fact, not during.</strong> A signed
         * {@code PUT} cannot bind the length of the body, so the provider
         * accepts an oversized upload and this application refuses it
         * afterwards, removing it. That ordering is unavoidable with presigned
         * uploads and is why confirming an upload is part of the flow rather
         * than optional.</p>
         */
        private DataSize maxUploadSize = DataSize.ofMegabytes(25);

        /**
         * MIME types a client may upload directly, lower case and without
         * parameters.
         *
         * <p>An allow-list rather than a deny-list, because the set of things
         * that should be storable is small and known while the set that should
         * not is not. A type absent from here is refused before a URL is
         * signed, so the bucket never accepts one.</p>
         *
         * <p><strong>Empty means nothing is uploadable</strong>, not everything.
         * Failing closed is the only safe reading of an absent list for a
         * control whose job is to keep things out.</p>
         *
         * <p>A replacement list, not an extension of the defaults: this is the
         * whole policy. Server-side writes are not subject to it — the letters
         * this application generates are its own bytes, and are stored whatever
         * format they are in.</p>
         */
        private Set<String> allowedContentTypes = new LinkedHashSet<>(
                List.of(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "image/png",
                        "image/jpeg"));
    }
}
