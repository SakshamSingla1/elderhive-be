package com.elderhive.services.impl;

import com.elderhive.dtos.CaretakerProfile.CaretakerProfileResponse;
import com.elderhive.entities.FavoriteCaretaker;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.FavoriteCaretakerRepository;
import com.elderhive.services.CaretakerProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteCaretakerServiceImplTest {

    private static final Long FAMILY_USER_ID = 10L;
    private static final Long CARETAKER_ID = 100L;

    @Mock
    private FavoriteCaretakerRepository favoriteCaretakerRepository;

    @Mock
    private CaretakerProfileService caretakerProfileService;

    @InjectMocks
    private FavoriteCaretakerServiceImpl service;

    private CaretakerProfileResponse caretakerResponse() {
        return CaretakerProfileResponse.builder().id(CARETAKER_ID).userId(200L)
                .verificationStatus(CaretakerVerificationStatusEnum.VERIFIED).build();
    }

    @Test
    void addingAnUnfavoritedCaretakerSavesTheRowAndReturnsTheirProfile() throws GenericException {
        when(caretakerProfileService.getById(CARETAKER_ID)).thenReturn(caretakerResponse());
        when(favoriteCaretakerRepository.existsByFamilyUserIdAndCaretakerId(FAMILY_USER_ID, CARETAKER_ID)).thenReturn(false);

        CaretakerProfileResponse response = service.addFavorite(FAMILY_USER_ID, CARETAKER_ID);

        assertThat(response.getId()).isEqualTo(CARETAKER_ID);
        verify(favoriteCaretakerRepository).save(any(FavoriteCaretaker.class));
    }

    @Test
    void addingAnUnknownCaretakerThrowsCaretakerProfileNotFound() throws GenericException {
        when(caretakerProfileService.getById(999L)).thenThrow(new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND, "Not found"));

        assertThatThrownBy(() -> service.addFavorite(FAMILY_USER_ID, 999L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND);

        verify(favoriteCaretakerRepository, never()).save(any());
    }

    @Test
    void addingAnAlreadyFavoritedCaretakerThrowsConflict() throws GenericException {
        when(caretakerProfileService.getById(CARETAKER_ID)).thenReturn(caretakerResponse());
        when(favoriteCaretakerRepository.existsByFamilyUserIdAndCaretakerId(FAMILY_USER_ID, CARETAKER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.addFavorite(FAMILY_USER_ID, CARETAKER_ID))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CONFLICT);

        verify(favoriteCaretakerRepository, never()).save(any());
    }

    @Test
    void removingAFavoritedCaretakerDeletesTheRow() throws GenericException {
        when(favoriteCaretakerRepository.findByFamilyUserIdAndCaretakerId(FAMILY_USER_ID, CARETAKER_ID))
                .thenReturn(Optional.of(FavoriteCaretaker.builder().id(1L).familyUserId(FAMILY_USER_ID).caretakerId(CARETAKER_ID).build()));

        service.removeFavorite(FAMILY_USER_ID, CARETAKER_ID);

        verify(favoriteCaretakerRepository).deleteByFamilyUserIdAndCaretakerId(FAMILY_USER_ID, CARETAKER_ID);
    }

    @Test
    void removingACaretakerThatIsNotFavoritedThrowsDataNotFound() {
        when(favoriteCaretakerRepository.findByFamilyUserIdAndCaretakerId(FAMILY_USER_ID, CARETAKER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeFavorite(FAMILY_USER_ID, CARETAKER_ID))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.DATA_NOT_FOUND);

        verify(favoriteCaretakerRepository, never()).deleteByFamilyUserIdAndCaretakerId(any(), any());
    }

    @Test
    void listFavoritesReturnsEveryFavoritedCaretakersProfile() throws GenericException {
        when(favoriteCaretakerRepository.findByFamilyUserIdOrderByCreatedAtDesc(FAMILY_USER_ID)).thenReturn(List.of(
                FavoriteCaretaker.builder().id(1L).familyUserId(FAMILY_USER_ID).caretakerId(CARETAKER_ID).build()
        ));
        when(caretakerProfileService.getById(CARETAKER_ID)).thenReturn(caretakerResponse());

        List<CaretakerProfileResponse> result = service.listFavorites(FAMILY_USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(CARETAKER_ID);
    }

    @Test
    void listFavoritesSkipsAFavoriteRowWhoseCaretakerProfileNoLongerResolves() throws GenericException {
        when(favoriteCaretakerRepository.findByFamilyUserIdOrderByCreatedAtDesc(FAMILY_USER_ID)).thenReturn(List.of(
                FavoriteCaretaker.builder().id(1L).familyUserId(FAMILY_USER_ID).caretakerId(999L).build()
        ));
        when(caretakerProfileService.getById(999L)).thenThrow(new GenericException(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND, "Not found"));

        List<CaretakerProfileResponse> result = service.listFavorites(FAMILY_USER_ID);

        assertThat(result).isEmpty();
    }
}
