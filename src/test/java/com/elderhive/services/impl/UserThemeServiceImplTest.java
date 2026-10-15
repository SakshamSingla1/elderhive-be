package com.elderhive.services.impl;

import com.elderhive.dao.user_theme.UserThemeMappingDao;
import com.elderhive.dtos.ColorTheme.ColorThemeResponse;
import com.elderhive.dtos.UserTheme.UserThemeResponse;
import com.elderhive.entities.User;
import com.elderhive.entities.UserThemeMapping;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.ColorThemeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserThemeServiceImplTest {

    @Mock
    private UserThemeMappingDao userThemeMappingDao;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ColorThemeService colorThemeService;

    @InjectMocks
    private UserThemeServiceImpl userThemeService;

    private User sampleUser(Long id) {
        return User.builder().id(id).email("user" + id + "@example.com").fullName("User " + id)
                .passwordHash("hashed").userType(UserTypeEnum.ELDER).status(UserStatusEnum.ACTIVE).build();
    }

    private ColorThemeResponse sampleTheme(Long id, String name) {
        return ColorThemeResponse.builder().id(id).themeName(name).build();
    }

    @Test
    void getThemeByUserIdReturnsTheMappedThemeWhenAMappingExists() throws GenericException {
        UserThemeMapping mapping = UserThemeMapping.builder().id(10L).userId(1L).themeId(5L).build();
        when(userThemeMappingDao.findByUserId(1L)).thenReturn(Optional.of(mapping));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser(1L)));
        when(colorThemeService.getThemeById(5L)).thenReturn(sampleTheme(5L, "Forest Green"));

        UserThemeResponse response = userThemeService.getThemeByUserId(1L);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getThemeId()).isEqualTo(5L);
        assertThat(response.getThemeName()).isEqualTo("Forest Green");
        verify(colorThemeService, never()).getDefaultTheme();
    }

    @Test
    void getThemeByUserIdFallsBackToTheDefaultThemeWhenNoMappingExists() throws GenericException {
        when(userThemeMappingDao.findByUserId(2L)).thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleUser(2L)));
        when(colorThemeService.getDefaultTheme()).thenReturn(sampleTheme(1L, "Platform Default"));

        UserThemeResponse response = userThemeService.getThemeByUserId(2L);

        assertThat(response.getId()).isNull();
        assertThat(response.getThemeId()).isEqualTo(1L);
        assertThat(response.getThemeName()).isEqualTo("Platform Default");
        verify(colorThemeService, never()).getThemeById(any());
    }

    @Test
    void getThemeByUserIdThrowsWhenTheUserDoesNotExistRegardlessOfMapping() {
        when(userThemeMappingDao.findByUserId(99L)).thenReturn(Optional.empty());
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userThemeService.getThemeByUserId(99L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.USER_NOT_FOUND);
    }

    @Test
    void setThemeForUserCreatesANewMappingWhenNoneExists() throws GenericException {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser(1L)));
        when(colorThemeService.getThemeById(7L)).thenReturn(sampleTheme(7L, "Ocean Blue"));
        // setThemeForUser re-fetches via getThemeByUserId after saving, so this must reflect
        // "no mapping yet" for the initial lookup and "freshly-saved mapping" afterward —
        // otherwise the re-fetch would wrongly see no mapping and fall back to the default.
        when(userThemeMappingDao.findByUserId(1L))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.of(UserThemeMapping.builder().id(20L).userId(1L).themeId(7L).build()));
        when(userThemeMappingDao.save(any(UserThemeMapping.class))).thenAnswer(invocation -> {
            UserThemeMapping saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        UserThemeResponse response = userThemeService.setThemeForUser(1L, 7L);

        ArgumentCaptor<UserThemeMapping> captor = ArgumentCaptor.forClass(UserThemeMapping.class);
        verify(userThemeMappingDao).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getThemeId()).isEqualTo(7L);
        assertThat(response.getId()).isEqualTo(20L);
        assertThat(response.getThemeId()).isEqualTo(7L);
        assertThat(response.getThemeName()).isEqualTo("Ocean Blue");
    }

    @Test
    void setThemeForUserUpdatesAnExistingMappingInPlace() throws GenericException {
        UserThemeMapping existing = UserThemeMapping.builder().id(20L).userId(1L).themeId(5L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser(1L)));
        when(colorThemeService.getThemeById(7L)).thenReturn(sampleTheme(7L, "Ocean Blue"));
        when(userThemeMappingDao.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(userThemeMappingDao.save(any(UserThemeMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserThemeResponse response = userThemeService.setThemeForUser(1L, 7L);

        assertThat(response.getId()).isEqualTo(20L);
        assertThat(response.getThemeId()).isEqualTo(7L);
        verify(userThemeMappingDao, never()).save(argThat_newMapping());
    }

    // A tiny local matcher to make "don't create a second row" explicit without a captor.
    private UserThemeMapping argThat_newMapping() {
        return org.mockito.ArgumentMatchers.argThat(m -> m.getId() == null);
    }

    @Test
    void setThemeForUserThrowsWhenTheUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userThemeService.setThemeForUser(99L, 7L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.USER_NOT_FOUND);

        verify(userThemeMappingDao, never()).save(any());
    }

    @Test
    void setThemeForUserPropagatesColorThemeNotFoundWhenTheThemeDoesNotExist() throws GenericException {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser(1L)));
        when(colorThemeService.getThemeById(404L))
                .thenThrow(new GenericException(ExceptionCodeEnum.COLOR_THEME_NOT_FOUND, "Theme not found: 404"));

        assertThatThrownBy(() -> userThemeService.setThemeForUser(1L, 404L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.COLOR_THEME_NOT_FOUND);

        verify(userThemeMappingDao, never()).save(any());
    }

    @Test
    void getUsersByThemeIdBatchLoadsEveryMappedUserInOneQueryRatherThanOnePerMapping() throws GenericException {
        UserThemeMapping m1 = UserThemeMapping.builder().id(1L).userId(1L).themeId(5L).build();
        UserThemeMapping m2 = UserThemeMapping.builder().id(2L).userId(2L).themeId(5L).build();
        when(userThemeMappingDao.findAllByThemeId(5L)).thenReturn(List.of(m1, m2));
        when(colorThemeService.getThemeById(5L)).thenReturn(sampleTheme(5L, "Forest Green"));
        when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(sampleUser(1L), sampleUser(2L)));

        List<UserThemeResponse> responses = userThemeService.getUsersByThemeId(5L);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(UserThemeResponse::getUserId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(responses).allMatch(r -> "Forest Green".equals(r.getThemeName()));
        verify(userRepository, times(1)).findAllById(any());
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void countUsersByThemeIdDelegatesToTheDao() {
        when(userThemeMappingDao.countByThemeId(5L)).thenReturn(3L);

        assertThat(userThemeService.countUsersByThemeId(5L)).isEqualTo(3L);
    }

    @Test
    void deleteThemeForUserRemovesAnExistingMapping() throws GenericException {
        when(userThemeMappingDao.existsByUserId(1L)).thenReturn(true);

        String message = userThemeService.deleteThemeForUser(1L);

        assertThat(message).isEqualTo("Theme reset to default successfully");
        verify(userThemeMappingDao).deleteByUserId(1L);
    }

    @Test
    void deleteThemeForUserThrowsWhenNoMappingExists() {
        when(userThemeMappingDao.existsByUserId(99L)).thenReturn(false);

        assertThatThrownBy(() -> userThemeService.deleteThemeForUser(99L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.DATA_NOT_FOUND);

        verify(userThemeMappingDao, never()).deleteByUserId(any());
    }
}
