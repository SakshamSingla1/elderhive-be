package com.elderhive.services.impl;

import com.elderhive.entities.NotificationTemplate;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.NotificationTemplateRepository;
import com.elderhive.services.RenderedNotification;
import com.elderhive.services.TemplateRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * No NotificationTemplateDao exists yet (same reasoning as UserThemeServiceImpl using
 * UserRepository directly, Prompt 44) — this is a single read-only lookup, and a DAO
 * wrapper gets added only once a later phase actually needs one.
 *
 * <p>Unlike Portfolio's NTServiceImpl#sendNotification, which loops over the CALLER-
 * supplied variables map and blindly .replace()s each key — silently leaving any
 * placeholder the caller forgot to supply untouched as a literal "{{typo}}" in the
 * output — this scans the template text itself for every {{placeholder}} occurrence and
 * requires a value for each one, throwing rather than shipping broken text. That gap is
 * exactly what this project's own notification work is meant to close (see
 * TemplateRenderer's own javadoc).
 */
@Service
@RequiredArgsConstructor
public class TemplateRendererImpl implements TemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final NotificationTemplateRepository notificationTemplateRepository;

    @Override
    public RenderedNotification render(String templateKey, Map<String, String> variables) throws GenericException {
        NotificationTemplate template = notificationTemplateRepository.findByTemplateKey(templateKey)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.NOTIFICATION_TEMPLATE_NOT_FOUND,
                        "No notification template registered for key: " + templateKey));

        String subject = substitute(template.getSubject(), variables, templateKey);
        String body = substitute(template.getMessageBody(), variables, templateKey);
        return new RenderedNotification(subject, body);
    }

    private String substitute(String text, Map<String, String> variables, String templateKey) throws GenericException {
        if (text == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value == null) {
                throw new GenericException(ExceptionCodeEnum.NOTIFICATION_TEMPLATE_PLACEHOLDER_UNRESOLVED,
                        "Template '" + templateKey + "' references {{" + key + "}} but no value was supplied");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
