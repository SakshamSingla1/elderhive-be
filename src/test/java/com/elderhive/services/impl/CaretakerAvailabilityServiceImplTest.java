package com.elderhive.services.impl;

import com.elderhive.dtos.CaretakerAvailability.CaretakerAvailabilityRequest;
import com.elderhive.dtos.CaretakerAvailability.CaretakerAvailabilityResponse;
import com.elderhive.entities.CaretakerAvailability;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.User;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.DayOfWeekEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.CaretakerAvailabilityRepository;
import com.elderhive.repositories.CaretakerProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaretakerAvailabilityServiceImplTest {

    @Mock
    private CaretakerAvailabilityRepository availabilityRepository;

    @Mock
    private CaretakerProfileRepository caretakerProfileRepository;

    @InjectMocks
    private CaretakerAvailabilityServiceImpl service;

    private User user(Long id, UserTypeEnum type) {
        return User.builder().id(id).email("user" + id + "@example.com").fullName("User " + id)
                .passwordHash("hashed").userType(type).status(UserStatusEnum.ACTIVE).build();
    }

    private CaretakerProfile profile(Long id, Long userId) {
        return CaretakerProfile.builder().id(id).userId(userId).verificationStatus(CaretakerVerificationStatusEnum.PENDING).build();
    }

    private CaretakerAvailabilityRequest request(DayOfWeekEnum day, LocalTime start, LocalTime end) {
        CaretakerAvailabilityRequest request = new CaretakerAvailabilityRequest();
        request.setDayOfWeek(day);
        request.setStartTime(start);
        request.setEndTime(end);
        return request;
    }

    @Test
    void creatingASlotForACaretakerWithAnExistingProfileSavesItAgainstThatProfile() throws GenericException {
        User caretaker = user(10L, UserTypeEnum.CARETAKER);
        when(caretakerProfileRepository.findByUserId(10L)).thenReturn(Optional.of(profile(100L, 10L)));
        when(availabilityRepository.save(any())).thenAnswer(inv -> {
            CaretakerAvailability a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        CaretakerAvailabilityResponse response = service.create(caretaker,
                request(DayOfWeekEnum.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)));

        assertThat(response.getCaretakerId()).isEqualTo(100L);
        assertThat(response.getDayOfWeek()).isEqualTo(DayOfWeekEnum.MONDAY);
        assertThat(response.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.getEndTime()).isEqualTo(LocalTime.of(17, 0));
    }

    @Test
    void creatingASlotWithNoCaretakerProfileThrowsCaretakerProfileNotFound() {
        User caretaker = user(10L, UserTypeEnum.CARETAKER);
        when(caretakerProfileRepository.findByUserId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(caretaker, request(DayOfWeekEnum.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0))))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND);

        verify(availabilityRepository, never()).save(any());
    }

    @Test
    void creatingASlotWithStartTimeNotBeforeEndTimeThrowsValidationFailed() {
        User caretaker = user(10L, UserTypeEnum.CARETAKER);
        when(caretakerProfileRepository.findByUserId(10L)).thenReturn(Optional.of(profile(100L, 10L)));

        assertThatThrownBy(() -> service.create(caretaker, request(DayOfWeekEnum.MONDAY, LocalTime.of(17, 0), LocalTime.of(9, 0))))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.VALIDATION_FAILED);

        verify(availabilityRepository, never()).save(any());
    }

    @Test
    void getByCaretakerIdReturnsEverySlotForThatCaretaker() throws GenericException {
        when(caretakerProfileRepository.existsById(100L)).thenReturn(true);
        when(availabilityRepository.findByCaretakerIdOrderByDayOfWeekAscStartTimeAsc(100L)).thenReturn(List.of(
                CaretakerAvailability.builder().id(1L).caretakerId(100L).dayOfWeek(DayOfWeekEnum.MONDAY)
                        .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0)).build()
        ));

        List<CaretakerAvailabilityResponse> result = service.getByCaretakerId(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDayOfWeek()).isEqualTo(DayOfWeekEnum.MONDAY);
    }

    @Test
    void getByCaretakerIdThrowsNotFoundForAnUnknownCaretaker() {
        when(caretakerProfileRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.getByCaretakerId(999L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND);
    }

    @Test
    void theOwningCaretakerCanUpdateTheirOwnSlot() throws GenericException {
        User caretaker = user(10L, UserTypeEnum.CARETAKER);
        CaretakerAvailability slot = CaretakerAvailability.builder()
                .id(1L).caretakerId(100L).dayOfWeek(DayOfWeekEnum.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0)).build();
        when(availabilityRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(caretakerProfileRepository.findByUserId(10L)).thenReturn(Optional.of(profile(100L, 10L)));
        when(availabilityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CaretakerAvailabilityResponse response = service.update(1L, caretaker,
                request(DayOfWeekEnum.TUESDAY, LocalTime.of(10, 0), LocalTime.of(18, 0)));

        assertThat(response.getDayOfWeek()).isEqualTo(DayOfWeekEnum.TUESDAY);
        assertThat(response.getStartTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void anAdminCanUpdateAnySlot() throws GenericException {
        User admin = user(99L, UserTypeEnum.SUPER_ADMIN);
        CaretakerAvailability slot = CaretakerAvailability.builder()
                .id(1L).caretakerId(100L).dayOfWeek(DayOfWeekEnum.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0)).build();
        when(availabilityRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(availabilityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CaretakerAvailabilityResponse response = service.update(1L, admin,
                request(DayOfWeekEnum.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)));

        assertThat(response.getDayOfWeek()).isEqualTo(DayOfWeekEnum.WEDNESDAY);
    }

    @Test
    void aStrangerCannotUpdateAnotherCaretakersSlot() {
        User stranger = user(20L, UserTypeEnum.CARETAKER);
        CaretakerAvailability slot = CaretakerAvailability.builder()
                .id(1L).caretakerId(100L).dayOfWeek(DayOfWeekEnum.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0)).build();
        when(availabilityRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(caretakerProfileRepository.findByUserId(20L)).thenReturn(Optional.of(profile(200L, 20L)));

        assertThatThrownBy(() -> service.update(1L, stranger, request(DayOfWeekEnum.TUESDAY, LocalTime.of(10, 0), LocalTime.of(18, 0))))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);

        verify(availabilityRepository, never()).save(any());
    }

    @Test
    void updatingAnUnknownSlotThrowsDataNotFound() {
        User caretaker = user(10L, UserTypeEnum.CARETAKER);
        when(availabilityRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, caretaker, request(DayOfWeekEnum.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0))))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.DATA_NOT_FOUND);
    }

    @Test
    void theOwningCaretakerCanDeleteTheirOwnSlot() throws GenericException {
        User caretaker = user(10L, UserTypeEnum.CARETAKER);
        CaretakerAvailability slot = CaretakerAvailability.builder().id(1L).caretakerId(100L).build();
        when(availabilityRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(caretakerProfileRepository.findByUserId(10L)).thenReturn(Optional.of(profile(100L, 10L)));

        service.delete(1L, caretaker);

        verify(availabilityRepository).deleteById(1L);
    }

    @Test
    void aStrangerCannotDeleteAnotherCaretakersSlot() {
        User stranger = user(20L, UserTypeEnum.CARETAKER);
        CaretakerAvailability slot = CaretakerAvailability.builder().id(1L).caretakerId(100L).build();
        when(availabilityRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(caretakerProfileRepository.findByUserId(20L)).thenReturn(Optional.of(profile(200L, 20L)));

        assertThatThrownBy(() -> service.delete(1L, stranger))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);

        verify(availabilityRepository, never()).deleteById(any());
    }
}
