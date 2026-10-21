package com.elderhive.services.impl;

import com.elderhive.dtos.AvailableSlot.AvailableSlotsResponse;
import com.elderhive.entities.Booking;
import com.elderhive.entities.CaretakerAvailability;
import com.elderhive.entities.ServiceOffering;
import com.elderhive.enums.BookingStatusEnum;
import com.elderhive.enums.DayOfWeekEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.BookingRepository;
import com.elderhive.repositories.CaretakerAvailabilityRepository;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.ServiceOfferingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailableSlotServiceImplTest {

    @Mock
    private CaretakerProfileRepository caretakerProfileRepository;

    @Mock
    private CaretakerAvailabilityRepository caretakerAvailabilityRepository;

    @Mock
    private ServiceOfferingRepository serviceOfferingRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private AvailableSlotServiceImpl service;

    // 2026-01-05 is a Monday.
    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);

    private ServiceOffering offering(Integer durationMinutes) {
        return ServiceOffering.builder().id(1L).name("Weekly Temple Visit").categoryId(1L).durationMinutes(durationMinutes).build();
    }

    private CaretakerAvailability window(DayOfWeekEnum day, String start, String end) {
        return CaretakerAvailability.builder().caretakerId(5L).dayOfWeek(day).startTime(LocalTime.parse(start)).endTime(LocalTime.parse(end)).build();
    }

    @Test
    void throwsCaretakerProfileNotFoundWhenTheCaretakerDoesNotExist() {
        when(caretakerProfileRepository.existsById(5L)).thenReturn(false);

        assertThatThrownBy(() -> service.getAvailableSlots(5L, MONDAY, 1L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND);
    }

    @Test
    void throwsServiceOfferingNotFoundWhenTheOfferingDoesNotExist() {
        when(caretakerProfileRepository.existsById(5L)).thenReturn(true);
        when(serviceOfferingRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAvailableSlots(5L, MONDAY, 1L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.SERVICE_OFFERING_NOT_FOUND);
    }

    @Test
    void fallsBackToTheDefaultNineToSixWindowAndFlagsItWhenNoCustomAvailabilityIsSet() throws GenericException {
        when(caretakerProfileRepository.existsById(5L)).thenReturn(true);
        when(serviceOfferingRepository.findById(1L)).thenReturn(Optional.of(offering(60)));
        when(caretakerAvailabilityRepository.findByCaretakerIdOrderByDayOfWeekAscStartTimeAsc(5L)).thenReturn(List.of());
        when(bookingRepository.findByCaretakerIdAndScheduledDateAndStatusIn(anyLong(), any(), any())).thenReturn(List.of());

        AvailableSlotsResponse response = service.getAvailableSlots(5L, MONDAY, 1L);

        assertThat(response.isUsingDefaultHours()).isTrue();
        assertThat(response.getDayOfWeek()).isEqualTo(DayOfWeekEnum.MONDAY);
        assertThat(response.getSlotDurationMinutes()).isEqualTo(60);
        assertThat(response.getAvailableSlots()).containsExactly(
                LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0),
                LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0), LocalTime.of(17, 0));
    }

    @Test
    void usesTheCaretakersOwnWeeklyScheduleWhenSetAndIgnoresOtherDaysOfWeek() throws GenericException {
        when(caretakerProfileRepository.existsById(5L)).thenReturn(true);
        when(serviceOfferingRepository.findById(1L)).thenReturn(Optional.of(offering(60)));
        when(caretakerAvailabilityRepository.findByCaretakerIdOrderByDayOfWeekAscStartTimeAsc(5L))
                .thenReturn(List.of(window(DayOfWeekEnum.MONDAY, "09:00", "12:00"), window(DayOfWeekEnum.TUESDAY, "09:00", "17:00")));
        when(bookingRepository.findByCaretakerIdAndScheduledDateAndStatusIn(anyLong(), any(), any())).thenReturn(List.of());

        AvailableSlotsResponse response = service.getAvailableSlots(5L, MONDAY, 1L);

        assertThat(response.isUsingDefaultHours()).isFalse();
        assertThat(response.getAvailableSlots()).containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0));
    }

    @Test
    void mergesMultipleWindowsOnTheSameDaySortedAndDeduplicated() throws GenericException {
        when(caretakerProfileRepository.existsById(5L)).thenReturn(true);
        when(serviceOfferingRepository.findById(1L)).thenReturn(Optional.of(offering(60)));
        when(caretakerAvailabilityRepository.findByCaretakerIdOrderByDayOfWeekAscStartTimeAsc(5L))
                .thenReturn(List.of(window(DayOfWeekEnum.MONDAY, "14:00", "16:00"), window(DayOfWeekEnum.MONDAY, "09:00", "11:00")));
        when(bookingRepository.findByCaretakerIdAndScheduledDateAndStatusIn(anyLong(), any(), any())).thenReturn(List.of());

        AvailableSlotsResponse response = service.getAvailableSlots(5L, MONDAY, 1L);

        assertThat(response.getAvailableSlots()).containsExactly(
                LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(14, 0), LocalTime.of(15, 0));
    }

    @Test
    void excludesSlotsAlreadyOccupiedByANonTerminalBookingButKeepsCancelledOnesFree() throws GenericException {
        when(caretakerProfileRepository.existsById(5L)).thenReturn(true);
        when(serviceOfferingRepository.findById(1L)).thenReturn(Optional.of(offering(60)));
        when(caretakerAvailabilityRepository.findByCaretakerIdOrderByDayOfWeekAscStartTimeAsc(5L))
                .thenReturn(List.of(window(DayOfWeekEnum.MONDAY, "09:00", "12:00")));
        Booking occupied = Booking.builder().caretakerId(5L).scheduledDate(MONDAY).scheduledTime(LocalTime.of(10, 0))
                .status(BookingStatusEnum.CONFIRMED).build();
        when(bookingRepository.findByCaretakerIdAndScheduledDateAndStatusIn(anyLong(), any(), any())).thenReturn(List.of(occupied));

        AvailableSlotsResponse response = service.getAvailableSlots(5L, MONDAY, 1L);

        assertThat(response.getAvailableSlots()).containsExactly(LocalTime.of(9, 0), LocalTime.of(11, 0));
    }

    @Test
    void defaultsTheSlotDurationTo60MinutesWhenTheOfferingHasNoneSet() throws GenericException {
        when(caretakerProfileRepository.existsById(5L)).thenReturn(true);
        when(serviceOfferingRepository.findById(1L)).thenReturn(Optional.of(offering(null)));
        when(caretakerAvailabilityRepository.findByCaretakerIdOrderByDayOfWeekAscStartTimeAsc(5L))
                .thenReturn(List.of(window(DayOfWeekEnum.MONDAY, "09:00", "10:00")));
        when(bookingRepository.findByCaretakerIdAndScheduledDateAndStatusIn(anyLong(), any(), any())).thenReturn(List.of());

        AvailableSlotsResponse response = service.getAvailableSlots(5L, MONDAY, 1L);

        assertThat(response.getSlotDurationMinutes()).isEqualTo(60);
        assertThat(response.getAvailableSlots()).containsExactly(LocalTime.of(9, 0));
    }

    @Test
    void sizesSlotsToTheOfferingsOwnDurationWhenShorterThanAnHour() throws GenericException {
        when(caretakerProfileRepository.existsById(5L)).thenReturn(true);
        when(serviceOfferingRepository.findById(1L)).thenReturn(Optional.of(offering(30)));
        when(caretakerAvailabilityRepository.findByCaretakerIdOrderByDayOfWeekAscStartTimeAsc(5L))
                .thenReturn(List.of(window(DayOfWeekEnum.MONDAY, "09:00", "10:00")));
        when(bookingRepository.findByCaretakerIdAndScheduledDateAndStatusIn(anyLong(), any(), any())).thenReturn(List.of());

        AvailableSlotsResponse response = service.getAvailableSlots(5L, MONDAY, 1L);

        assertThat(response.getAvailableSlots()).containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30));
    }
}
