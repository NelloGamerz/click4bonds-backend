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

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StorageProperties.class)
    static class Binding {
    }
}
