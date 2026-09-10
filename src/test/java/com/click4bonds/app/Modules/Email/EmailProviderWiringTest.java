package com.click4bonds.app.Modules.Email;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.click4bonds.app.Modules.Email.Config.EmailProperties;
import com.click4bonds.app.Modules.Email.Exception.UnsupportedEmailProviderException;
import com.click4bonds.app.Modules.Email.Provider.EmailProvider;
import com.click4bonds.app.Modules.Email.Provider.EmailProviderFactory;
import com.click4bonds.app.Modules.Email.Provider.EmailProviderType;
import com.click4bonds.app.Modules.Email.Provider.Resend.ResendEmailProvider;
import com.resend.Resend;

/**
 * Boots a minimal Spring context (no datasource, no Redis) to prove that the
 * {@code email.provider} property drives which {@link EmailProvider} the
 * application ends up with.
 */
class EmailProviderWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(Resend.class, () -> mock(Resend.class))
            .withPropertyValues("resend.from=Click4Bond <no-reply@click4bond.com>")
            .withUserConfiguration(
                    EmailPropertiesConfig.class,
                    ResendEmailProvider.class,
                    EmailProviderFactory.class);

    @Test
    void shouldWireResendProviderWhenProviderIsResend() {

        contextRunner
                .withPropertyValues("email.provider=resend")
                .run(context -> {

                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EmailProvider.class);

                    assertThat(context.getBean(EmailProvider.class))
                            .isInstanceOf(ResendEmailProvider.class)
                            .extracting(EmailProvider::type)
                            .isEqualTo(EmailProviderType.RESEND);
                });
    }

    @Test
    void shouldFailToStartWhenConfiguredProviderIsUnknown() {

        contextRunner
                .withPropertyValues("email.provider=mailchimp")
                .run(context -> {

                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(UnsupportedEmailProviderException.class)
                            .rootCause()
                            .hasMessageContaining("mailchimp");
                });
    }

    @EnableConfigurationProperties(EmailProperties.class)
    static class EmailPropertiesConfig {
    }
}
