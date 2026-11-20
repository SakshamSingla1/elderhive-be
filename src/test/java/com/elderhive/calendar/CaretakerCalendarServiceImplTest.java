package com.elderhive.calendar;

import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.ElderProfile;
import com.elderhive.entities.ServiceOffering;
import com.elderhive.enums.BookingStatusEnum;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.BookingRepository;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.ElderProfileRepository;
import com.elderhive.repositories.ServiceOfferingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaretakerCalendarServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long CARETAKER_ID = 1L;

    @Mock
    private CaretakerProfileRepository caretakerProfileRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ElderProfileRepository elderProfileRepository;
    @Mock
    private ServiceOfferingRepository serviceOfferingRepository;

    @InjectMocks
    private CaretakerCalendarServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "appUrl", "https://elderhive-be.onrender.com");
    }

    private CaretakerProfile profile(String token) {
        return CaretakerProfile.builder().id(CARETAKER_ID).userId(USER_ID)
                .verificationStatus(CaretakerVerificationStatusEnum.VERIFIED).calendarShareToken(token).build();
    }

    @Test
    void getOrCreateCalendarLinkGeneratesATokenOnFirstCall() throws GenericException {
        CaretakerProfile profile = profile(null);
        when(caretakerProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(caretakerProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String link = service.getOrCreateCalendarLink(USER_ID);

        ArgumentCaptor<CaretakerProfile> captor = ArgumentCaptor.forClass(CaretakerProfile.class);
        verify(caretakerProfileRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getCalendarShareToken()).isNotBlank();
        assertThat(link).isEqualTo("https://elderhive-be.onrender.com/api/v1/caretakers/me/calendar.ics?token="
                + captor.getValue().getCalendarShareToken());
    }

    @Test
    void getOrCreateCalendarLinkReusesAnExistingTokenWithoutSaving() throws GenericException {
        CaretakerProfile profile = profile("existing-token");
        when(caretakerProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));

        String link = service.getOrCreateCalendarLink(USER_ID);

        verify(caretakerProfileRepository, never()).save(any());
        assertThat(link).endsWith("token=existing-token");
    }

    @Test
    void regenerateCalendarLinkAlwaysIssuesANewTokenEvenWhenOneAlreadyExists() throws GenericException {
        CaretakerProfile profile = profile("old-token");
        when(caretakerProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(caretakerProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String link = service.regenerateCalendarLink(USER_ID);

        assertThat(link).doesNotContain("old-token");
        assertThat(profile.getCalendarShareToken()).isNotEqualTo("old-token");
    }

    @Test
    void getOrCreateCalendarLinkThrowsWhenNoCaretakerProfileExistsForTheUser() {
        when(caretakerProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrCreateCalendarLink(USER_ID))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND);
    }

    @Test
    void buildIcsFeedThrowsForAnUnknownToken() {
        when(caretakerProfileRepository.findByCalendarShareToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildIcsFeed("bad-token"))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND);
    }

    @Test
    void buildIcsFeedRendersOneStableVeventPerUpcomingConfirmedOrInProgressBooking() throws GenericException {
        CaretakerProfile profile = profile("real-token");
        when(caretakerProfileRepository.findByCalendarShareToken("real-token")).thenReturn(Optional.of(profile));

        Booking booking = Booking.builder().id(555L).caretakerId(CARETAKER_ID).elderProfileId(20L).serviceOfferingId(30L)
                .scheduledDate(LocalDate.of(2026, 12, 1)).scheduledTime(LocalTime.of(10, 0))
                .status(BookingStatusEnum.CONFIRMED).build();
        when(bookingRepository.findByCaretakerIdAndStatusInAndScheduledDateGreaterThanEqualOrderByScheduledDateAscScheduledTimeAsc(
                eq(CARETAKER_ID), eq(List.of(BookingStatusEnum.CONFIRMED, BookingStatusEnum.IN_PROGRESS)), any()))
                .thenReturn(List.of(booking));
        when(elderProfileRepository.findById(20L)).thenReturn(Optional.of(ElderProfile.builder().id(20L).fullName("Grandma Rose").build()));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(ServiceOffering.builder().id(30L).name("Companionship").durationMinutes(90).build()));

        String ics = service.buildIcsFeed("real-token");

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n");
        assertThat(ics).contains("UID:elderhive-booking-555@elderhive.app\r\n");
        assertThat(ics).contains("DTSTART:20261201T100000\r\n");
        assertThat(ics).contains("DTEND:20261201T113000\r\n");
        assertThat(ics).contains("SUMMARY:Companionship with Grandma Rose\r\n");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
    }

    @Test
    void buildIcsFeedFallsBackToTheDefaultDurationWhenTheServiceOfferingHasNoneSet() throws GenericException {
        CaretakerProfile profile = profile("real-token");
        when(caretakerProfileRepository.findByCalendarShareToken("real-token")).thenReturn(Optional.of(profile));

        Booking booking = Booking.builder().id(556L).caretakerId(CARETAKER_ID).elderProfileId(20L).serviceOfferingId(30L)
                .scheduledDate(LocalDate.of(2026, 12, 1)).scheduledTime(LocalTime.of(10, 0))
                .status(BookingStatusEnum.IN_PROGRESS).build();
        when(bookingRepository.findByCaretakerIdAndStatusInAndScheduledDateGreaterThanEqualOrderByScheduledDateAscScheduledTimeAsc(
                eq(CARETAKER_ID), any(), any()))
                .thenReturn(List.of(booking));
        when(elderProfileRepository.findById(20L)).thenReturn(Optional.of(ElderProfile.builder().id(20L).fullName("Grandma Rose").build()));
        when(serviceOfferingRepository.findById(30L)).thenReturn(Optional.of(ServiceOffering.builder().id(30L).name("Companionship").durationMinutes(null).build()));

        String ics = service.buildIcsFeed("real-token");

        assertThat(ics).contains("DTEND:20261201T110000\r\n");
    }

    @Test
    void buildIcsFeedProducesNoEventsWhenThereAreNoUpcomingBookings() throws GenericException {
        CaretakerProfile profile = profile("real-token");
        when(caretakerProfileRepository.findByCalendarShareToken("real-token")).thenReturn(Optional.of(profile));
        when(bookingRepository.findByCaretakerIdAndStatusInAndScheduledDateGreaterThanEqualOrderByScheduledDateAscScheduledTimeAsc(
                eq(CARETAKER_ID), any(), any())).thenReturn(List.of());

        String ics = service.buildIcsFeed("real-token");

        assertThat(ics).doesNotContain("BEGIN:VEVENT");
        assertThat(ics).contains("BEGIN:VCALENDAR").contains("END:VCALENDAR");
    }
}
