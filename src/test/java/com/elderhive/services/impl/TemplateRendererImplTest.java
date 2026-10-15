package com.elderhive.services.impl;

import com.elderhive.entities.NotificationTemplate;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.NotificationTemplateRepository;
import com.elderhive.services.RenderedNotification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateRendererImplTest {

    @Mock
    private NotificationTemplateRepository notificationTemplateRepository;

    @InjectMocks
    private TemplateRendererImpl templateRenderer;

    @Test
    void substitutesEveryPlaceholderInBothSubjectAndBody() throws GenericException {
        NotificationTemplate template = NotificationTemplate.builder()
                .templateKey("booking.confirmed")
                .subject("Your booking with {{caretakerName}} is confirmed")
                .messageBody("Hi {{elderName}}, your booking with {{caretakerName}} on {{date}} is confirmed.")
                .build();
        when(notificationTemplateRepository.findByTemplateKey("booking.confirmed")).thenReturn(Optional.of(template));

        Map<String, String> variables = Map.of(
                "caretakerName", "Jane Doe",
                "elderName", "Robert Smith",
                "date", "Oct 20"
        );

        RenderedNotification rendered = templateRenderer.render("booking.confirmed", variables);

        assertThat(rendered.subject()).isEqualTo("Your booking with Jane Doe is confirmed");
        assertThat(rendered.body()).isEqualTo("Hi Robert Smith, your booking with Jane Doe on Oct 20 is confirmed.");
    }

    @Test
    void repeatedPlaceholdersAreAllSubstituted() throws GenericException {
        NotificationTemplate template = NotificationTemplate.builder()
                .templateKey("reminder")
                .subject("{{name}}, {{name}} - reminder")
                .messageBody("Hello {{name}}")
                .build();
        when(notificationTemplateRepository.findByTemplateKey("reminder")).thenReturn(Optional.of(template));

        RenderedNotification rendered = templateRenderer.render("reminder", Map.of("name", "Alex"));

        assertThat(rendered.subject()).isEqualTo("Alex, Alex - reminder");
        assertThat(rendered.body()).isEqualTo("Hello Alex");
    }

    @Test
    void aTemplateWithNoPlaceholdersAtAllRendersVerbatim() throws GenericException {
        NotificationTemplate template = NotificationTemplate.builder()
                .templateKey("welcome")
                .subject("Welcome to ElderHive")
                .messageBody("Thanks for joining.")
                .build();
        when(notificationTemplateRepository.findByTemplateKey("welcome")).thenReturn(Optional.of(template));

        RenderedNotification rendered = templateRenderer.render("welcome", Map.of());

        assertThat(rendered.subject()).isEqualTo("Welcome to ElderHive");
        assertThat(rendered.body()).isEqualTo("Thanks for joining.");
    }

    @Test
    void aNullSubjectOrBodyRendersAsNullRatherThanThrowing() throws GenericException {
        NotificationTemplate template = NotificationTemplate.builder()
                .templateKey("sms-only")
                .subject(null)
                .messageBody("{{code}} is your code")
                .build();
        when(notificationTemplateRepository.findByTemplateKey("sms-only")).thenReturn(Optional.of(template));

        RenderedNotification rendered = templateRenderer.render("sms-only", Map.of("code", "123456"));

        assertThat(rendered.subject()).isNull();
        assertThat(rendered.body()).isEqualTo("123456 is your code");
    }

    @Test
    void unknownTemplateKeyThrowsNotificationTemplateNotFound() {
        when(notificationTemplateRepository.findByTemplateKey("does-not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateRenderer.render("does-not-exist", Map.of()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.NOTIFICATION_TEMPLATE_NOT_FOUND);
    }

    @Test
    void aPlaceholderWithNoSuppliedValueThrowsRatherThanShippingALiteralBraces() {
        NotificationTemplate template = NotificationTemplate.builder()
                .templateKey("incomplete")
                .subject("Hi {{name}}")
                .messageBody("body")
                .build();
        when(notificationTemplateRepository.findByTemplateKey("incomplete")).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> templateRenderer.render("incomplete", Map.of()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.NOTIFICATION_TEMPLATE_PLACEHOLDER_UNRESOLVED);
    }

    @Test
    void extraSuppliedVariablesTheTemplateDoesNotReferenceAreSilentlyIgnored() throws GenericException {
        NotificationTemplate template = NotificationTemplate.builder()
                .templateKey("simple")
                .subject("Hi {{name}}")
                .messageBody("body")
                .build();
        when(notificationTemplateRepository.findByTemplateKey("simple")).thenReturn(Optional.of(template));

        Map<String, String> variables = new HashMap<>();
        variables.put("name", "Pat");
        variables.put("unused", "whatever");

        RenderedNotification rendered = templateRenderer.render("simple", variables);

        assertThat(rendered.subject()).isEqualTo("Hi Pat");
    }

    @Test
    void doesNotMistakeAMalformedSinglePlaceholderForAWellFormedOne() throws GenericException {
        NotificationTemplate template = NotificationTemplate.builder()
                .templateKey("malformed")
                .subject("{name} is not {{name}}")
                .messageBody("body")
                .build();
        when(notificationTemplateRepository.findByTemplateKey("malformed")).thenReturn(Optional.of(template));

        RenderedNotification rendered = templateRenderer.render("malformed", Map.of("name", "Sam"));

        // Single-brace "{name}" is left untouched - only the well-formed "{{name}}" is a
        // real placeholder.
        assertThat(rendered.subject()).isEqualTo("{name} is not Sam");
    }
}
