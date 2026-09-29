package com.click4bonds.app.Modules.Storage.Config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

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

        /** R2 API token access key id. */
        private String accessKeyId = "";

        /**
         * R2 API token secret.
         *
         * <p>Read together with {@link #accessKeyId}. When both are blank the
         * provider's own credential chain is used instead — environment
         * variables, a shared profile, or an instance role — which is how a
         * deployment that does not want keys in its configuration supplies
         * them.</p>
         */
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
    }
}
