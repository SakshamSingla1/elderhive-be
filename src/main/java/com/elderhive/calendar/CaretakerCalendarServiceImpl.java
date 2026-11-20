package com.elderhive.calendar;

import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.ServiceOffering;
import com.elderhive.enums.BookingStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.BookingRepository;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.ServiceOfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaretakerCalendarServiceImpl implements CaretakerCalendarService {

    private static final List<BookingStatusEnum> ICS_STATUSES = List.of(BookingStatusEnum.CONFIRMED, BookingStatusEnum.IN_PROGRESS);
    // Floating local time (no TZID/Z) - Booking's scheduledDate/scheduledTime carry no
    // timezone anywhere in this app's data model, so the importing calendar app interprets
    // these in ITS OWN local timezone, same simplification the rest of the booking flow
    // already makes.
    private static final DateTimeFormatter LOCAL_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final int DEFAULT_DURATION_MINUTES = 60;

    private final CaretakerProfileRepository caretakerProfileRepository;
    private final BookingRepository bookingRepository;
    private final ElderProfileRepository elderProfileRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;

    @Value("${app.url}")
    private String appUrl;

    @Override
    @Transactional
    public String getOrCreateCalendarLink(Long userId) throws GenericException {
        CaretakerProfile profile = findByUserId(userId);
        if (profile.getCalendarShareToken() == null) {
            profile.setCalendarShareToken(UUID.randomUUID().toString());
            caretakerProfileRepository.save(profile);
        }
        return buildLink(profile.getCalendarShareToken());
    }

    @Override
    @Transactional
    public String regenerateCalendarLink(Long userId) throws GenericException {
        CaretakerProfile profile = findByUserId(userId);
        profile.setCalendarShareToken(UUID.randomUUID().toString());
        caretakerProfileRepository.save(profile);
        return buildLink(profile.getCalendarShareToken());
    }

    @Override
    public String buildIcsFeed(String token) throws GenericException {
        CaretakerProfile profile = caretakerProfileRepository.findByCalendarShareToken(token)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND, "Invalid calendar token"));

        List<Booking> bookings = bookingRepository
                .findByCaretakerIdAndStatusInAndScheduledDateGreaterThanEqualOrderByScheduledDateAscScheduledTimeAsc(
                        profile.getId(), ICS_STATUSES, LocalDate.now());

        String dtstamp = ZonedDateTime.now(ZoneOffset.UTC).format(UTC_TIMESTAMP_FORMAT);
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//ElderHive//Caretaker Calendar//EN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        for (Booking booking : bookings) {
            appendEvent(sb, booking, dtstamp);
        }
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private void appendEvent(StringBuilder sb, Booking booking, String dtstamp) {
        if (booking.getScheduledDate() == null || booking.getScheduledTime() == null) {
            return;
        }
        String elderName = elderProfileRepository.findById(booking.getElderProfileId())
                .map(ElderProfile::getFullName).orElse("Elder");
        ServiceOffering offering = serviceOfferingRepository.findById(booking.getServiceOfferingId()).orElse(null);
        String serviceName = offering != null && offering.getName() != null ? offering.getName() : "Service";
        int durationMinutes = offering != null && offering.getDurationMinutes() != null
                ? offering.getDurationMinutes() : DEFAULT_DURATION_MINUTES;

        LocalDateTime start = LocalDateTime.of(booking.getScheduledDate(), booking.getScheduledTime());
        LocalDateTime end = start.plusMinutes(durationMinutes);

        sb.append("BEGIN:VEVENT\r\n");
        // Stable across re-fetches (the booking id never changes) so the importing
        // calendar app updates the existing event instead of duplicating it every sync.
        sb.append("UID:elderhive-booking-").append(booking.getId()).append("@elderhive.app\r\n");
        sb.append("DTSTAMP:").append(dtstamp).append("\r\n");
        sb.append("DTSTART:").append(start.format(LOCAL_DATE_TIME_FORMAT)).append("\r\n");
        sb.append("DTEND:").append(end.format(LOCAL_DATE_TIME_FORMAT)).append("\r\n");
        sb.append("SUMMARY:").append(escapeIcsText(serviceName + " with " + elderName)).append("\r\n");
        sb.append("END:VEVENT\r\n");
    }

    private CaretakerProfile findByUserId(Long userId) throws GenericException {
        return caretakerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND, "Caretaker profile not found"));
    }

    private String buildLink(String token) {
        return appUrl + "/api/v1/caretakers/me/calendar.ics?token=" + token;
    }

    // Minimal RFC5545 §3.3.11 TEXT escaping - backslash first, so it doesn't double-escape
    // the backslashes this method itself just inserted for the other three characters.
    private String escapeIcsText(String text) {
        return text.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n");
    }
}
