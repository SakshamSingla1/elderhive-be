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
import com.elderhive.services.AdminDashboardService;
import com.elderhive.services.EmergencyAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.Map;

/**
 * One dedicated service per the admin-controller-per-concern pattern (mirroring
 * ~/DSA_Java/backend's own AdminDashboardController/AdminDashboardService split) - pulls
 * real counts from the entities built so far rather than any pre-aggregated table.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final EmergencyAlertService emergencyAlertService;
    private final CaretakerProfileRepository caretakerProfileRepository;

    @Override
    public AdminDashboardStatsResponse getStats() throws GenericException {
        Map<UserTypeEnum, Long> activeUsersByRole = new EnumMap<>(UserTypeEnum.class);
        for (UserTypeEnum userType : UserTypeEnum.values()) {
            activeUsersByRole.put(userType, userRepository.countByUserTypeAndStatus(userType, UserStatusEnum.ACTIVE));
        }

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        long bookingsThisWeek = bookingRepository.countByScheduledDateBetween(weekStart, weekEnd);

        EmergencyAlertOpenCounts alertCounts = emergencyAlertService.countOpenAlerts();

        long pendingCaretakerVerifications =
                caretakerProfileRepository.countByVerificationStatus(CaretakerVerificationStatusEnum.PENDING);

        return AdminDashboardStatsResponse.builder()
                .activeUsersByRole(activeUsersByRole)
                .bookingsThisWeek(bookingsThisWeek)
                .openEmergencyAlerts(alertCounts.getOpen())
                .overdueEmergencyAlerts(alertCounts.getOverdue())
                .pendingCaretakerVerifications(pendingCaretakerVerifications)
                .build();
    }
}
