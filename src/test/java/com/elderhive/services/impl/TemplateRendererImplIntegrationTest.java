package com.elderhive.services.impl;

import com.elderhive.entities.NotificationTemplate;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.NotificationTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.NotificationTemplateRepository;
import com.elderhive.services.RenderedNotification;
import com.elderhive.services.TemplateRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real TemplateRenderer bean against a real NotificationTemplate row in
 * Postgres, not a mocked repository — proves the whole wiring (bean injection, the real
 * query, the real substitution) works end to end, the same discipline applied to every
 * other service in this codebase (e.g. ColorThemeServiceImplCachingTest).
 */
@SpringBootTest
@Transactional
class TemplateRendererImplIntegrationTest {

    @Autowired
    private TemplateRenderer templateRenderer;

    @Autowired
    private NotificationTemplateRepository notificationTemplateRepository;

    @Test
    void rendersARealPersistedTemplateEndToEnd() throws GenericException {
        notificationTemplateRepository.save(NotificationTemplate.builder()
                .templateKey("integration.booking-confirmed")
                .type(NotificationTypeEnum.BOOKING)
                .subject("Booking confirmed with {{caretakerName}}")
                .messageBody("Hi {{elderName}}, {{caretakerName}} will visit on {{date}}.")
                .isInApp(true)
                .isEmail(true)
                .build());

        RenderedNotification rendered = templateRenderer.render("integration.booking-confirmed", Map.of(
                "caretakerName", "Jane Doe",
                "elderName", "Robert",
                "date", "Oct 20"
        ));

        assertThat(rendered.subject()).isEqualTo("Booking confirmed with Jane Doe");
        assertThat(rendered.body()).isEqualTo("Hi Robert, Jane Doe will visit on Oct 20.");
    }

    @Test
    void realLookupThrowsNotificationTemplateNotFoundForAnUnregisteredKey() {
        assertThatThrownBy(() -> templateRenderer.render("integration.does-not-exist", Map.of()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.NOTIFICATION_TEMPLATE_NOT_FOUND);
    }
}
