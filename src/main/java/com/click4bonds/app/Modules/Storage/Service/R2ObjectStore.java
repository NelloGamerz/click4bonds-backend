package com.click4bonds.app.Modules.Storage.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.click4bonds.app.Modules.Storage.Config.StorageProperties;
import com.click4bonds.app.Modules.Storage.Exception.StorageException;
import com.click4bonds.app.Modules.Storage.Model.ObjectMetadata;
import com.click4bonds.app.Modules.Storage.Model.StoredObject;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Cloudflare R2, through its S3-compatible API.
 *
 * <p>R2 speaks enough of S3 that the standard AWS client is the supported way to
 * reach it; the differences that matter are all in configuration and are set
 * here rather than left to a caller to remember.</p>
 *
 * <h2>Why the client is configured the way it is</h2>
 *
 * <p>R2 is close enough to S3 that the standard client drives it, but not close
 * enough that its defaults work. Three settings are load-bearing, and each of
 * them fails in a way that looks like an authentication problem rather than a
 * configuration one.</p>
 *
 * <p><strong>Path-style addressing.</strong> R2 serves objects from the
 * account-scoped endpoint with the bucket in the path
 * ({@code .../<bucket>/<key>}). The client's default is to move the bucket into
 * the hostname, which produces a name that does not resolve.</p>
 *
 * <p><strong>Chunked encoding is off.</strong> The SDK's default upload uses
 * {@code aws-chunked} transfer encoding, and R2 rejects the resulting signature
 * with a 403 that reads as though the credentials were wrong. Every object this
 * application stores is a byte array whose length is already known, so the
 * streaming form buys nothing.</p>
 *
 * <p><strong>Checksums are not computed unless the operation requires one.</strong>
 * From version 2.30.0 the AWS SDK adds a CRC32 checksum to uploads by default and
 * asks the service to validate one on downloads. R2 implements neither and
 * rejects the request outright — "the header
 * {@code x-amz-checksum-algorithm} with value {@code CRC32} is not implemented".
 * Both settings are pinned to {@code WHEN_REQUIRED}, which restores the
 * pre-2.30.0 behaviour. Removing them does not downgrade anything; it breaks
 * every write.</p>
 *
 * <h2>Why construction touches nothing</h2>
 *
 * <p>No network call and no validation happens here. The endpoint, bucket and
 * credentials are all optional as far as starting up is concerned: a machine with
 * document generation switched off, or a test that only loads the context, must
 * not fail to boot because a bucket is unreachable. A missing value is reported
 * by the operation that needed it, naming the property, which is also when an
 * operator can act on it.</p>
 */
@Service
@Slf4j
public class R2ObjectStore implements ObjectStore {

    private final StorageProperties.R2 config;

    private final S3Client client;

    private final S3Presigner presigner;

    /**
     * The constructor Spring uses.
     *
     * <p>Marked explicitly because the class has a second, test-only constructor.
     * Given more than one and no annotation, Spring looks for a no-argument one
     * and fails — the dependency it needs would be reported as a missing default
     * constructor rather than as anything to do with storage.</p>
     */
    @Autowired
    public R2ObjectStore(StorageProperties properties) {

        this(properties, null);
    }

    /**
     * Test seam: an already-built client, so the mapping between an operation and
     * its request can be exercised without a bucket.
     *
     * @param client the client to use, or null to build one from the properties
     */
    R2ObjectStore(StorageProperties properties, S3Client client) {

        this.config = properties.getR2();

        this.client = client != null ? client : buildClient();

        this.presigner = buildPresigner();

        log.info(
                "Object store configured: bucket={} endpoint={} staticCredentials={}",
                config.getBucket(),
                config.getEndpoint(),
                StringUtils.hasText(config.getAccessKeyId()));
    }

    @Override
    public StoredObject put(String key, byte[] content, String contentType) {

        String bucket = requireEndpointAndBucket();

        requireKey(key);

        try {

            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));

            log.info(
                    "Object stored: key={} bytes={} contentType={}",
                    key,
                    content.length,
                    contentType);

            return new StoredObject(key, contentType, content.length);

        } catch (SdkException failure) {

            throw new StorageException(
                    "Failed to store object at key " + key + " in bucket " + bucket,
                    failure);
        }
    }

    @Override
    public void copy(String sourceKey, String destinationKey, String contentType) {

        String bucket = requireEndpointAndBucket();

        requireKey(sourceKey);

        requireKey(destinationKey);

        if (!StringUtils.hasText(contentType)) {

            /*
             * Rejected rather than left to the provider. A copy without a
             * metadata directive keeps the source's content type, which sounds
             * harmless and is not: this operation exists to move an upload that
             * has just been checked as a specific type, and a copy that stored
             * it as something else would put an object in the bucket that
             * nothing afterwards agrees about.
             */
            throw new StorageException(
                    "A copy needs the content type the object is to be stored as");
        }

        try {

            /*
             * R2 addresses the source by bucket and key in one header, as S3
             * does. The keys this application copies are built from
             * [A-Za-z0-9._/-] — see UploadService's key derivation — so there is
             * nothing in one that needs escaping.
             */
            client.copyObject(
                    CopyObjectRequest.builder()
                            .sourceBucket(bucket)
                            .sourceKey(sourceKey)
                            .destinationBucket(bucket)
                            .destinationKey(destinationKey)
                            /*
                             * REPLACE, not COPY: without it the copy inherits the
                             * source's metadata and the content type set above is
                             * ignored.
                             */
                            .metadataDirective(MetadataDirective.REPLACE)
                            .contentType(contentType)
                            .build());

            log.info(
                    "Object copied: sourceKey={} destinationKey={} bucket={}",
                    sourceKey,
                    destinationKey,
                    bucket);

        } catch (SdkException failure) {

            throw new StorageException(
                    "Failed to copy object from key " + sourceKey + " to key " + destinationKey
                            + " in bucket " + bucket,
                    failure);
        }
    }

    @Override
    public byte[] get(String key) {

        String bucket = requireEndpointAndBucket();

        requireKey(key);

        try (ResponseInputStream<GetObjectResponse> object = client.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build())) {

            return object.readAllBytes();

        } catch (SdkException | IOException failure) {

            throw new StorageException(
                    "Failed to read object at key " + key + " in bucket " + bucket,
                    failure);
        }
    }

    @Override
    public boolean exists(String key) {

        String bucket = requireEndpointAndBucket();

        requireKey(key);

        try {

            client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build());

            return true;

        } catch (S3Exception failure) {

            /*
             * A missing object is an answer, not an error. Everything else — a
             * rejected credential, an unreachable endpoint — is a failure to
             * answer and must not be reported as "it is not there", which a
             * caller would act on by regenerating a document that may well
             * exist.
             */
            if (failure.statusCode() == 404) {
                return false;
            }

            throw new StorageException(
                    "Failed to check for object at key " + key + " in bucket " + bucket,
                    failure);

        } catch (SdkException failure) {

            throw new StorageException(
                    "Failed to check for object at key " + key + " in bucket " + bucket,
                    failure);
        }
    }

    @Override
    public Optional<ObjectMetadata> stat(String key) {

        String bucket = requireEndpointAndBucket();

        requireKey(key);

        try {

            HeadObjectResponse head = client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build());

            /*
             * The provider's reply is the only place a size can be had for an
             * object this application did not write — a client upload goes
             * straight to the bucket — so it is read here and carried back
             * rather than checked against anything.
             */
            return Optional.of(
                    new ObjectMetadata(
                            key,
                            head.contentType(),
                            head.contentLength() == null ? 0L : head.contentLength(),
                            head.eTag(),
                            head.lastModified()));

        } catch (S3Exception failure) {

            /*
             * Same reasoning as exists: a missing object is an answer, and empty
             * is how it is given. Anything else is a failure to answer.
             */
            if (failure.statusCode() == 404) {
                return Optional.empty();
            }

            throw new StorageException(
                    "Failed to read metadata for object at key " + key + " in bucket " + bucket,
                    failure);

        } catch (SdkException failure) {

            throw new StorageException(
                    "Failed to read metadata for object at key " + key + " in bucket " + bucket,
                    failure);
        }
    }

    @Override
    public void delete(String key) {

        String bucket = requireEndpointAndBucket();

        requireKey(key);

        try {

            client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build());

            log.info("Object deleted: key={} bucket={}", key, bucket);

        } catch (SdkException failure) {

            throw new StorageException(
                    "Failed to delete object at key " + key + " in bucket " + bucket,
                    failure);
        }
    }

    @Override
    public URI urlFor(String key) {

        String endpoint = requireEndpoint();

        String bucket = requireBucket();

        requireKey(key);

        /*
         * Path-style, matching how the client addresses the bucket: the bucket is
         * a path segment, not a subdomain. A recorded address that was built the
         * other way would not be the URL the object actually lives at.
         */
        return URI.create(endpoint + "/" + bucket + "/" + key);
    }

    @Override
    public String keyFor(String location) {

        if (!StringUtils.hasText(location)) {
            throw new StorageException("Object location is required");
        }

        URI address;

        try {

            address = new URI(location);

        } catch (URISyntaxException failure) {

            throw new StorageException("Not a usable object location: " + location, failure);
        }

        /*
         * No scheme means a bare relative key. This store addressed objects by
         * key before it had addresses at all, and rows written then still hold
         * one; refusing them would strand those documents to no purpose.
         */
        if (address.getScheme() == null) {

            requireKey(location);

            return location;
        }

        String expectedHost = URI.create(requireEndpoint()).getHost();

        if (!StringUtils.hasText(address.getHost())
                || !address.getHost().equalsIgnoreCase(expectedHost)) {

            /*
             * A mismatch between a recorded address and the configuration now in
             * force. Named rather than absorbed: reading the key out of it anyway
             * would fetch from a bucket the address does not point at, and look
             * like a success.
             */
            throw new StorageException(
                    "Object location was not written by this endpoint (expected "
                            + expectedHost + "): " + location);
        }

        String prefix = "/" + requireBucket() + "/";

        String path = address.getPath();

        if (path == null || !path.startsWith(prefix)) {

            throw new StorageException(
                    "Object location is not in the configured bucket (expected "
                            + requireBucket() + "): " + location);
        }

        String key = path.substring(prefix.length());

        requireKey(key);

        return key;
    }

    @Override
    public URI presignedGetUrl(String key, Duration ttl) {

        String bucket = requireEndpointAndBucket();

        requireKey(key);

        requirePositiveLifetime(ttl, "A presigned URL needs a positive lifetime");

        try {

            return URI.create(
                    presigner.presignGetObject(
                                    GetObjectPresignRequest.builder()
                                            .signatureDuration(ttl)
                                            .getObjectRequest(
                                                    GetObjectRequest.builder()
                                                            .bucket(bucket)
                                                            .key(key)
                                                            .build())
                                            .build())
                            .url()
                            .toString());

        } catch (SdkException failure) {

            throw new StorageException(
                    "Failed to sign a URL for key " + key + " in bucket " + bucket,
                    failure);
        }
    }

    @Override
    public URI presignedPutUrl(String key, String contentType, Duration ttl) {

        String bucket = requireEndpointAndBucket();

        requireKey(key);

        if (!StringUtils.hasText(contentType)) {
            throw new StorageException(
                    "A presigned upload URL needs a content type: the provider signs it "
                            + "into the URL and rejects an upload that sends a different one");
        }

        requirePositiveLifetime(ttl, "A presigned upload URL needs a positive lifetime");

        try {

            /*
             * The content type is set on the request rather than left to the
             * client, because setting it is what makes the provider sign it.
             * That turns it into a constraint the bucket enforces: a client
             * cannot use the URL to store anything but the type that was
             * authorised.
             */
            return URI.create(
                    presigner.presignPutObject(
                                    PutObjectPresignRequest.builder()
                                            .signatureDuration(ttl)
                                            .putObjectRequest(
                                                    PutObjectRequest.builder()
                                                            .bucket(bucket)
                                                            .key(key)
                                                            .contentType(contentType)
                                                            .build())
                                            .build())
                            .url()
                            .toString());

        } catch (SdkException failure) {

            throw new StorageException(
                    "Failed to sign an upload URL for key " + key + " in bucket " + bucket,
                    failure);
        }
    }

    /**
     * @throws StorageException with this message when the lifetime is absent or
     *         not positive. Rejected here rather than left to the presigner,
     *         which would otherwise sign a URL that expired before it was
     *         returned — a failure that looks like the client being refused
     *         rather than the lifetime being wrong
     */
    private void requirePositiveLifetime(Duration ttl, String message) {

        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new StorageException(message);
        }
    }

    /**
     * Releases the HTTP connection pool and its threads.
     *
     * <p>Both clients hold one. Without this a shutdown waits on them, and a test
     * that builds several stores leaks a pool per store.</p>
     */
    @PreDestroy
    public void close() {

        client.close();
        presigner.close();
    }

    /**
     * The settings that make the client speak R2 rather than AWS.
     *
     * <p>Shared by the client and the presigner, because a signed URL is only
     * usable if it was signed for the same endpoint and addressing style the
     * client uploads to. Signing path-style while uploading virtual-hosted would
     * produce a URL that 404s.</p>
     *
     * <p>Package-private so a test can assert both flags without a bucket: they
     * are the difference between working and a 403, and neither is observable
     * from a mocked client.</p>
     */
    static S3Configuration r2ServiceConfiguration() {

        return S3Configuration.builder()
                /*
                 * R2 puts the bucket in the path. The client's default is to put
                 * it in the hostname, which R2 does not serve.
                 */
                .pathStyleAccessEnabled(true)
                /*
                 * The default aws-chunked upload produces a signature R2 rejects
                 * with a 403 that looks like a bad credential.
                 */
                .chunkedEncodingEnabled(false)
                .build();
    }

    private S3Client buildClient() {

        var builder = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(r2ServiceConfiguration())
                /*
                 * R2 rejects the CRC32 checksum the SDK adds by default from
                 * 2.30.0. See the class javadoc: without these two, every write
                 * fails.
                 */
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                /*
                 * A sick bucket must fail a request rather than hold its thread.
                 * The download endpoint reads through this client.
                 */
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(config.getApiCallTimeout())
                        .apiCallAttemptTimeout(config.getApiCallAttemptTimeout())
                        .build());

        URI endpoint = endpointUriOrNull();

        if (endpoint != null) {
            builder.endpointOverride(endpoint);
        }

        return builder.build();
    }

    private S3Presigner buildPresigner() {

        var builder = S3Presigner.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(r2ServiceConfiguration());

        URI endpoint = endpointUriOrNull();

        if (endpoint != null) {
            builder.endpointOverride(endpoint);
        }

        return builder.build();
    }

    /**
     * The configured endpoint, or null when none is set.
     *
     * <p>Absent and malformed are treated differently on purpose. Absent is a
     * legitimate state — documents may be switched off, and a laptop or CI
     * machine has no bucket — so it must not stop the context loading, and the
     * operation that needed the store reports it instead. Malformed is never
     * legitimate, and it is caught here rather than left to the SDK: handing it
     * {@code URI.create("host-without-a-scheme")} produces a bare
     * NullPointerException about a null scheme, which says nothing about which
     * setting to fix.</p>
     */
    private URI endpointUriOrNull() {

        if (!StringUtils.hasText(config.getEndpoint())) {
            return null;
        }

        return URI.create(requireEndpoint());
    }

    /**
     * The token's two halves when both are configured, otherwise the provider's
     * own chain.
     *
     * <p>Falling back rather than failing is what lets a deployment that cannot
     * hold keys in its configuration — an instance with a role, or a developer
     * with a shared profile — use this store unchanged.</p>
     */
    private AwsCredentialsProvider credentialsProvider() {

        if (StringUtils.hasText(config.getAccessKeyId())
                && StringUtils.hasText(config.getSecretAccessKey())) {

            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            config.getAccessKeyId(),
                            config.getSecretAccessKey()));
        }

        return DefaultCredentialsProvider.create();
    }

    /**
     * Both settings an operation against the bucket needs, checked together.
     *
     * <p>Separate from {@link #requireBucket} because
     * {@link #keyFor} resolves a bare key — which needs neither — and must not
     * demand an endpoint it is not going to use.</p>
     */
    private String requireEndpointAndBucket() {

        requireEndpoint();

        return requireBucket();
    }

    /**
     * @throws StorageException naming the property, because the alternative is an
     *         SDK error several frames down that reports a parameter was empty
     *         without saying which setting produced it
     */
    private String requireBucket() {

        if (!StringUtils.hasText(config.getBucket())) {
            throw new StorageException(
                    "No bucket configured: set storage.r2.bucket (R2_BUCKET)");
        }

        return config.getBucket();
    }

    /**
     * The endpoint as an absolute URL, without a trailing slash.
     *
     * <p>Trimmed rather than validated-and-returned raw so that an operator's
     * habit of writing a trailing slash does not produce
     * {@code https://host//bucket/key} in a recorded address.</p>
     *
     * @throws StorageException naming the property, and saying what shape it
     *         should be, because a missing scheme or host would otherwise surface
     *         as a malformed URL built from it
     */
    private String requireEndpoint() {

        if (!StringUtils.hasText(config.getEndpoint())) {
            throw new StorageException(
                    "No object store endpoint configured: set storage.r2.endpoint (R2_ENDPOINT)");
        }

        String endpoint = config.getEndpoint().trim();

        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }

        URI parsed;

        try {
            parsed = URI.create(endpoint);

        } catch (IllegalArgumentException failure) {

            throw new StorageException(
                    "storage.r2.endpoint is not a valid URL: " + endpoint, failure);
        }

        if (parsed.getScheme() == null || parsed.getHost() == null) {

            throw new StorageException(
                    "storage.r2.endpoint must be absolute, of the form "
                            + "https://<account-id>.r2.cloudflarestorage.com: " + endpoint);
        }

        return endpoint;
    }

    private void requireKey(String key) {

        if (!StringUtils.hasText(key)) {
            throw new StorageException("Object key is required");
        }

        /*
         * An object key cannot escape a bucket the way a filename can escape a
         * directory, so this is not the same guard the filesystem
         * implementation needed. It is here because a leading slash or a ".."
         * segment means the caller has built a path, and taken from a filesystem
         * by mistake — the object would be written under a key nothing else
         * agrees on, which is worse than a rejected write because it looks like
         * it succeeded.
         */
        if (key.startsWith("/")) {
            throw new StorageException("Object key must be relative: " + key);
        }

        for (String segment : key.split("/")) {

            if (segment.equals("..")) {
                throw new StorageException("Object key must not contain a parent segment: " + key);
            }
        }
    }
}
