package com.click4bonds.app.Modules.Storage.Config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * That the property names in the YAML reach the fields.
 *
 * <p>Worth a test because a mistyped key binds to nothing and fails silently.
 * Spring ignores properties no field claims, so {@code storage.r2.secret-key}
 * instead of {@code secret-access-key} would leave the secret blank, fall back
 * to the SDK's default credential chain, and surface much later as an
 * authentication error against R2 — with nothing pointing at the spelling.</p>
 *
 * <p>These are exactly the names written in {@code application.yaml} and
 * {@code application-prod.yaml}; if this test and those files disagree, one of
 * them is wrong.</p>
 */
class StoragePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Binding.class);

    @Test
    void bindsEveryDocumentedProperty() {

        runner.withPropertyValues(
                        "storage.r2.endpoint=https://abc123.r2.cloudflarestorage.com",
                        "storage.r2.access-key-id=an-access-key",
                        "storage.r2.secret-access-key=a-secret",
                        "storage.r2.bucket=click4bonds-documents",
                        "storage.r2.region=auto")
                .run(context -> {

                    StorageProperties.R2 r2 =
                            context.getBean(StorageProperties.class).getR2();

                    assertThat(r2.getEndpoint())
                            .isEqualTo("https://abc123.r2.cloudflarestorage.com");
                    assertThat(r2.getAccessKeyId()).isEqualTo("an-access-key");
                    assertThat(r2.getSecretAccessKey()).isEqualTo("a-secret");
                    assertThat(r2.getBucket()).isEqualTo("click4bonds-documents");
                    assertThat(r2.getRegion()).isEqualTo("auto");
                });
    }

    @Test
    void defaultsToR2sOwnRegionAndBoundedCalls() {

        runner.run(context -> {

            StorageProperties.R2 r2 = context.getBean(StorageProperties.class).getR2();

            /*
             * R2 ignores the region but SigV4 signing demands one, and "auto" is
             * R2's convention. A default that had to be supplied would be a
             * required setting that is never actually a decision.
             */
            assertThat(r2.getRegion()).isEqualTo("auto");

            assertThat(r2.getApiCallTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(r2.getApiCallAttemptTimeout()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    void bindsTheUploadPolicy() {

        runner.withPropertyValues(
                        "storage.r2.presigned-upload-ttl=3m",
                        "storage.r2.presigned-download-ttl=90s",
                        "storage.r2.max-upload-size=5MB",
                        "storage.r2.allowed-content-types[0]=application/pdf",
                        "storage.r2.allowed-content-types[1]=image/png")
                .run(context -> {

                    StorageProperties.R2 r2 =
                            context.getBean(StorageProperties.class).getR2();

                    assertThat(r2.getPresignedUploadTtl()).isEqualTo(Duration.ofMinutes(3));
                    assertThat(r2.getPresignedDownloadTtl()).isEqualTo(Duration.ofSeconds(90));
                    assertThat(r2.getMaxUploadSize().toBytes()).isEqualTo(5L * 1024 * 1024);
                    assertThat(r2.getAllowedContentTypes())
                            .containsExactly("application/pdf", "image/png");
                });
    }

    @Test
    void defaultsToAUsableUploadPolicy() {

        runner.run(context -> {

            StorageProperties.R2 r2 = context.getBean(StorageProperties.class).getR2();

            /*
             * A presigned URL is handed to a browser, and anyone holding it has
             * the access it grants until it expires — so the defaults are short.
             * Uploads get the longer window because the person on the other end
             * has to find a file and send it; a download starts as soon as the
             * URL is issued.
             */
            assertThat(r2.getPresignedUploadTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(r2.getPresignedDownloadTtl()).isEqualTo(Duration.ofMinutes(5));

            assertThat(r2.getMaxUploadSize().toBytes()).isEqualTo(25L * 1024 * 1024);

            /*
             * The document types this platform publishes. A replacement list
             * rather than an extension of these: this is the whole policy, and a
             * deployment that wants something else states what it wants.
             */
            assertThat(r2.getAllowedContentTypes()).containsExactly(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "image/png",
                    "image/jpeg");
        });
    }

    @Test
    void leavesCredentialsBlankWhenNothingIsConfigured() {

        runner.run(context -> {

            StorageProperties.R2 r2 = context.getBean(StorageProperties.class).getR2();

            /*
             * Blank rather than defaulted. These are credentials: a value that
             * worked out of the box would be a value everybody could read.
             */
            assertThat(r2.getAccessKeyId()).isEmpty();
            assertThat(r2.getSecretAccessKey()).isEmpty();
            assertThat(r2.getBucket()).isEmpty();
            assertThat(r2.getEndpoint()).isEmpty();
        });
    }

    /**
     * The credentials must not reach anything that prints this bean.
     *
     * <p>{@code @Data} generates a {@code toString} over every field, so a
     * binding failure, a debugger's object view, a test that prints the bean or
     * a future {@code log.debug("{}", properties)} would write the R2 secret —
     * a token with write access to every object in the bucket — alongside the
     * endpoint it belongs to.</p>
     *
     * <p>Asserted on the values rather than on the field names, because the
     * property under test is that the secret is absent, not how Lombok chose to
     * omit it. Asserted on the nested object as well as the bean: both carry
     * {@code @Data}, so excluding the field on one would leave the other
     * printing it.</p>
     */
    @Test
    void doesNotPrintCredentialsInToString() {

        runner.withPropertyValues(
                        "storage.r2.endpoint=https://abc123.r2.cloudflarestorage.com",
                        "storage.r2.access-key-id=an-access-key",
                        "storage.r2.secret-access-key=a-secret-that-must-not-be-printed",
                        "storage.r2.bucket=click4bonds-documents")
                .run(context -> {

                    StorageProperties properties =
                            context.getBean(StorageProperties.class);

                    assertThat(properties.toString())
                            .doesNotContain("a-secret-that-must-not-be-printed")
                            .doesNotContain("an-access-key");

                    assertThat(properties.getR2().toString())
                            .doesNotContain("a-secret-that-must-not-be-printed")
                            .doesNotContain("an-access-key");

                    /*
                     * And the exclusions did not swallow the object: what a
                     * person reading a binding failure actually needs — which
                     * endpoint and bucket the deployment was pointed at — is
                     * still there.
                     */
                    assertThat(properties.getR2().toString())
                            .contains("https://abc123.r2.cloudflarestorage.com")
                            .contains("click4bonds-documents");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StorageProperties.class)
    static class Binding {
    }
}
