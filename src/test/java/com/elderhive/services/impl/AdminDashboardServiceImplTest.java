package com.elderhive.services.impl;

import com.elderhive.dtos.Dashboard.AdminDashboardStatsResponse;
import com.elderhive.dtos.Emergency.EmergencyAlertOpenCounts;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.BookingRepository;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.EmergencyAlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private EmergencyAlertService emergencyAlertService;
    @Mock
    private CaretakerProfileRepository caretakerProfileRepository;

    @InjectMocks
    private AdminDashboardServiceImpl service;

    @Test
    void activeUsersByRoleHasOneEntryPerUserTypeEvenWhenSomeAreZero() throws GenericException {
        when(userRepository.countByUserTypeAndStatus(UserTypeEnum.SUPER_ADMIN, UserStatusEnum.ACTIVE)).thenReturn(1L);
        when(userRepository.countByUserTypeAndStatus(UserTypeEnum.ADMIN, UserStatusEnum.ACTIVE)).thenReturn(2L);
        when(userRepository.countByUserTypeAndStatus(UserTypeEnum.ELDER, UserStatusEnum.ACTIVE)).thenReturn(0L);
        when(userRepository.countByUserTypeAndStatus(UserTypeEnum.CARETAKER, UserStatusEnum.ACTIVE)).thenReturn(5L);
        when(userRepository.countByUserTypeAndStatus(UserTypeEnum.FAMILY_MEMBER, UserStatusEnum.ACTIVE)).thenReturn(10L);
        when(bookingRepository.countByScheduledDateBetween(any(), any())).thenReturn(0L);
        when(emergencyAlertService.countOpenAlerts()).thenReturn(EmergencyAlertOpenCounts.builder().open(0).overdue(0).build());
        when(caretakerProfileRepository.countByVerificationStatus(CaretakerVerificationStatusEnum.PENDING)).thenReturn(0L);

        AdminDashboardStatsResponse stats = service.getStats();

        assertThat(stats.getActiveUsersByRole()).hasSize(5);
        assertThat(stats.getActiveUsersByRole())
                .containsEntry(UserTypeEnum.SUPER_ADMIN, 1L)
                .containsEntry(UserTypeEnum.ADMIN, 2L)
                .containsEntry(UserTypeEnum.ELDER, 0L)
                .containsEntry(UserTypeEnum.CARETAKER, 5L)
                .containsEntry(UserTypeEnum.FAMILY_MEMBER, 10L);
    }

    @Test
    void bookingsThisWeekQueriesMondayThroughSundayOfTheCurrentWeek() throws GenericException {
        when(userRepository.countByUserTypeAndStatus(any(), any())).thenReturn(0L);
        when(bookingRepository.countByScheduledDateBetween(any(), any())).thenReturn(7L);
        when(emergencyAlertService.countOpenAlerts()).thenReturn(EmergencyAlertOpenCounts.builder().open(0).overdue(0).build());
        when(caretakerProfileRepository.countByVerificationStatus(CaretakerVerificationStatusEnum.PENDING)).thenReturn(0L);

        AdminDashboardStatsResponse stats = service.getStats();

        LocalDate today = LocalDate.now();
        LocalDate expectedMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate expectedSunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        verify(bookingRepository).countByScheduledDateBetween(expectedMonday, expectedSunday);
        assertThat(stats.getBookingsThisWeek()).isEqualTo(7);
    }

    @Test
    void passesThroughTheEmergencyAlertAndCaretakerVerificationCounts() throws GenericException {
        when(userRepository.countByUserTypeAndStatus(any(), any())).thenReturn(0L);
        when(bookingRepository.countByScheduledDateBetween(any(), any())).thenReturn(0L);
        when(emergencyAlertService.countOpenAlerts()).thenReturn(EmergencyAlertOpenCounts.builder().open(4).overdue(2).build());
        when(caretakerProfileRepository.countByVerificationStatus(CaretakerVerificationStatusEnum.PENDING)).thenReturn(3L);

        AdminDashboardStatsResponse stats = service.getStats();

        assertThat(stats.getOpenEmergencyAlerts()).isEqualTo(4);
        assertThat(stats.getOverdueEmergencyAlerts()).isEqualTo(2);
        assertThat(stats.getPendingCaretakerVerifications()).isEqualTo(3);
    }
}
