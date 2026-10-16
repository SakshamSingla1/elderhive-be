package com.elderhive.repositories;

import com.elderhive.entities.NotificationTemplate;
import com.elderhive.enums.NotificationTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms V16's seed migration actually landed the 5 template keys Phase 10/11 will need,
 * with sane placeholder-bearing copy and the channel flags documented in the migration's own
 * comment. Not @Transactional - this data comes from the migration itself (already committed
 * outside any test transaction), so this only reads, never writes.
 */
@SpringBootTest
class NotificationTemplateSeedIntegrationTest {

    @Autowired
    private NotificationTemplateRepository notificationTemplateRepository;

    private NotificationTemplate find(String templateKey) {
        return notificationTemplateRepository.findByTemplateKey(templateKey)
                .orElseThrow(() -> new AssertionError("Seeded template missing: " + templateKey));
    }

    @Test
    void bookingConfirmedIsSeededWithTheExpectedPlaceholdersAndChannels() {
        NotificationTemplate template = find("BOOKING_CONFIRMED");

        assertThat(template.getType()).isEqualTo(NotificationTypeEnum.BOOKING);
        assertThat(template.getMessageBody()).contains("{{elderName}}", "{{serviceName}}", "{{bookingDate}}");
        assertThat(template.isInApp()).isTrue();
        assertThat(template.isEmail()).isTrue();
        assertThat(template.isWebPush()).isTrue();
    }

    @Test
    void bookingCancelledIsSeededWithTheExpectedPlaceholdersAndChannels() {
        NotificationTemplate template = find("BOOKING_CANCELLED");

        assertThat(template.getType()).isEqualTo(NotificationTypeEnum.BOOKING);
        assertThat(template.getMessageBody()).contains("{{elderName}}", "{{serviceName}}", "{{bookingDate}}");
        assertThat(template.isInApp()).isTrue();
        assertThat(template.isEmail()).isTrue();
    }

    @Test
    void bookingCompletedIsSeededAsLowerUrgencyWithNoWebPushOrSms() {
        NotificationTemplate template = find("BOOKING_COMPLETED");

        assertThat(template.getType()).isEqualTo(NotificationTypeEnum.BOOKING);
        assertThat(template.getMessageBody()).contains("{{elderName}}", "{{serviceName}}", "{{bookingDate}}");
        assertThat(template.isSms()).isFalse();
        assertThat(template.isWebPush()).isFalse();
    }

    @Test
    void emergencyAlertFamilyIsSeededWithEveryChannelEnabled() {
        NotificationTemplate template = find("EMERGENCY_ALERT_FAMILY");

        assertThat(template.getType()).isEqualTo(NotificationTypeEnum.EMERGENCY);
        assertThat(template.getMessageBody()).contains("{{elderName}}", "{{alertAddress}}");
        assertThat(template.isInApp()).isTrue();
        assertThat(template.isEmail()).isTrue();
        assertThat(template.isSms()).isTrue();
        assertThat(template.isWebPush()).isTrue();
    }

    @Test
    void emergencyAlertCaretakerIsSeededWithoutEmailButWithSmsAndWebPush() {
        NotificationTemplate template = find("EMERGENCY_ALERT_CARETAKER");

        assertThat(template.getType()).isEqualTo(NotificationTypeEnum.EMERGENCY);
        assertThat(template.getMessageBody()).contains("{{elderName}}", "{{alertAddress}}");
        assertThat(template.isEmail()).isFalse();
        assertThat(template.isSms()).isTrue();
        assertThat(template.isWebPush()).isTrue();
    }
}
