package com.elderhive.services.impl;

import com.elderhive.dao.user_theme.UserThemeMappingDao;
import com.elderhive.dtos.ColorTheme.ColorThemeResponse;
import com.elderhive.dtos.UserTheme.UserThemeResponse;
import com.elderhive.entities.User;
import com.elderhive.entities.UserThemeMapping;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.UserRepository;
import com.elderhive.services.ColorThemeService;
import com.elderhive.services.UserThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Straight rename of Portfolio's ProfileThemeServiceImpl (profile -&gt; user), including its
 * concurrent-fetch optimization (the mapping and user lookups are independent, so fetching
 * them in parallel instead of sequentially avoids paying two round trips back-to-back) and
 * its N+1-avoiding batch load in getUsersByThemeId. The one behavioral deviation —
 * getThemeByUserId falling back to the platform default theme instead of throwing when no
 * mapping row exists — is documented on the interface method itself.
 *
 * <p>Uses ColorThemeService (not a ColorThemeDao, unlike Portfolio) for every ColorTheme
 * read here, including the mapped-theme lookup Portfolio does via its DAO directly — this
 * keeps both the cached-lookup and the fallback-to-default path going through the exact
 * same cached, business-rule-aware entry point rather than splitting theme reads across two
 * different layers for no real benefit. No UserDao exists in ElderHive yet (only
 * UserRepository), so this uses it directly — the same precedent AuthServiceImpl already
 * set, and DAO-wrapping an entity is only added when a prompt actually asks for one (see
 * ColorThemeDao, Prompt 42).
 */
@Service
@RequiredArgsConstructor
public class UserThemeServiceImpl implements UserThemeService {

    private final UserThemeMappingDao userThemeMappingDao;
    private final UserRepository userRepository;
    private final ColorThemeService colorThemeService;

    @Override
    public UserThemeResponse getThemeByUserId(Long userId) throws GenericException {
        CompletableFuture<Optional<UserThemeMapping>> mappingF =
                CompletableFuture.supplyAsync(() -> userThemeMappingDao.findByUserId(userId));
        CompletableFuture<Optional<User>> userF =
                CompletableFuture.supplyAsync(() -> userRepository.findById(userId));

        Optional<UserThemeMapping> mappingOpt = mappingF.join();
        User user = userF.join()
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found: " + userId));

        if (mappingOpt.isEmpty()) {
            ColorThemeResponse defaultTheme = colorThemeService.getDefaultTheme();
            return mapToResponse(null, userId, user, defaultTheme.getId(), defaultTheme.getThemeName());
        }

        UserThemeMapping mapping = mappingOpt.get();
        ColorThemeResponse theme = colorThemeService.getThemeById(mapping.getThemeId());
        return mapToResponse(mapping, userId, user, theme.getId(), theme.getThemeName());
    }

    @Override
    public UserThemeResponse setThemeForUser(Long userId, Long themeId) throws GenericException {
        userRepository.findById(userId)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.USER_NOT_FOUND, "User not found: " + userId));

        colorThemeService.getThemeById(themeId);

        UserThemeMapping mapping = userThemeMappingDao.findByUserId(userId)
                .orElse(UserThemeMapping.builder().userId(userId).build());
        mapping.setThemeId(themeId);
        userThemeMappingDao.save(mapping);

        return getThemeByUserId(userId);
    }

    @Override
    public List<UserThemeResponse> getUsersByThemeId(Long themeId) throws GenericException {
        List<UserThemeMapping> mappings = userThemeMappingDao.findAllByThemeId(themeId);
        ColorThemeResponse theme = colorThemeService.getThemeById(themeId);

        // Batch-load every mapped user in one query instead of one findById per mapping
        // (N+1) — a popular theme can be mapped to many users.
        List<Long> userIds = mappings.stream()
                .map(UserThemeMapping::getUserId)
                .collect(Collectors.toList());
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return mappings.stream()
                .map(mapping -> mapToResponse(mapping, mapping.getUserId(), usersById.get(mapping.getUserId()),
                        theme.getId(), theme.getThemeName()))
                .collect(Collectors.toList());
    }

    @Override
    public long countUsersByThemeId(Long themeId) {
        return userThemeMappingDao.countByThemeId(themeId);
    }

    @Override
    public String deleteThemeForUser(Long userId) throws GenericException {
        if (!userThemeMappingDao.existsByUserId(userId)) {
            throw new GenericException(ExceptionCodeEnum.DATA_NOT_FOUND, "Theme mapping not found for user: " + userId);
        }
        userThemeMappingDao.deleteByUserId(userId);
        return "Theme reset to default successfully";
    }

    private UserThemeResponse mapToResponse(UserThemeMapping mapping, Long userId, User user,
                                             Long themeId, String themeName) {
        return UserThemeResponse.builder()
                .id(mapping != null ? mapping.getId() : null)
                .userId(userId)
                .userEmail(user != null ? user.getEmail() : null)
                .userFullName(user != null ? user.getFullName() : null)
                .themeId(themeId)
                .themeName(themeName)
                .createdAt(mapping != null ? mapping.getCreatedAt() : null)
                .updatedAt(mapping != null ? mapping.getUpdatedAt() : null)
                .createdBy(mapping != null ? mapping.getCreatedBy() : null)
                .updatedBy(mapping != null ? mapping.getUpdatedBy() : null)
                .build();
    }
}
