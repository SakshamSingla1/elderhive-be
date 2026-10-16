-- Seeds the template keys Phase 10 (bookings) and Phase 11 (emergency alerts) will wire
-- real callers to. Channel flags are deliberately NOT all-true: a completed booking is
-- low-urgency (in-app + email only), while an emergency alert is high-urgency and gets
-- every channel the respective audience plausibly has (family: all four; the caretaker
-- receiving it is expected to already be in the app/on push rather than email).
INSERT INTO notification_templates (template_key, type, subject, message_body, is_in_app, is_email, is_sms, is_web_push, created_at, updated_at)
VALUES
    ('BOOKING_CONFIRMED', 'BOOKING', 'Booking confirmed',
     'Hi {{elderName}}, your {{serviceName}} booking on {{bookingDate}} has been confirmed.',
     true, true, false, true, now(), now()),

    ('BOOKING_CANCELLED', 'BOOKING', 'Booking cancelled',
     'Hi {{elderName}}, your {{serviceName}} booking on {{bookingDate}} has been cancelled.',
     true, true, false, true, now(), now()),

    ('BOOKING_COMPLETED', 'BOOKING', 'Booking completed',
     'Hi {{elderName}}, your {{serviceName}} booking on {{bookingDate}} has been marked as completed. We hope it went well!',
     true, true, false, false, now(), now()),

    ('EMERGENCY_ALERT_FAMILY', 'EMERGENCY', 'Emergency alert',
     'An emergency alert has been triggered for {{elderName}} at {{alertAddress}}. Please check on them as soon as possible.',
     true, true, true, true, now(), now()),

    ('EMERGENCY_ALERT_CARETAKER', 'EMERGENCY', 'Emergency alert - immediate response needed',
     'An emergency alert has been triggered for {{elderName}} at {{alertAddress}}. Please respond immediately.',
     true, false, true, true, now(), now());
