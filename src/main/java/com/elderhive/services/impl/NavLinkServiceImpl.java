package com.elderhive.services.impl;

import com.elderhive.dtos.NavLink.NavLinkRequest;
import com.elderhive.dtos.NavLink.NavLinkResponse;
import com.elderhive.entities.NavLink;
import com.elderhive.entities.User;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.NavLinkStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.NavLinkRepository;
import com.elderhive.security.AdminPermissionGuard;
import com.elderhive.services.NavLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NavLinkServiceImpl implements NavLinkService {

    private final NavLinkRepository navLinkRepository;
    private final AdminPermissionGuard adminPermissionGuard;

    @Override
    @Transactional
    public NavLinkResponse create(NavLinkRequest request) throws GenericException {
        NavLink navLink = NavLink.builder()
                .navGroup(request.getNavGroup())
                .navIndex(request.getNavIndex() != null ? request.getNavIndex() : 0)
                .name(request.getName())
                .path(request.getPath())
                .icon(request.getIcon())
                .requiredPermission(request.getRequiredPermission())
                .superAdminOnly(Boolean.TRUE.equals(request.getSuperAdminOnly()))
                .status(request.getStatus() != null ? request.getStatus() : NavLinkStatusEnum.ACTIVE)
                .build();
        return toResponse(navLinkRepository.save(navLink));
    }

    @Override
    @Transactional
    public NavLinkResponse update(Long id, NavLinkRequest request) throws GenericException {
        NavLink navLink = findOrThrow(id);
        navLink.setNavGroup(request.getNavGroup());
        if (request.getNavIndex() != null) {
            navLink.setNavIndex(request.getNavIndex());
        }
        navLink.setName(request.getName());
        navLink.setPath(request.getPath());
        navLink.setIcon(request.getIcon());
        navLink.setRequiredPermission(request.getRequiredPermission());
        navLink.setSuperAdminOnly(Boolean.TRUE.equals(request.getSuperAdminOnly()));
        if (request.getStatus() != null) {
            navLink.setStatus(request.getStatus());
        }
        return toResponse(navLinkRepository.save(navLink));
    }

    @Override
    public NavLinkResponse getById(Long id) throws GenericException {
        return toResponse(findOrThrow(id));
    }

    @Override
    public Page<NavLinkResponse> getAll(Pageable pageable) {
        return navLinkRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public void delete(Long id) throws GenericException {
        NavLink navLink = findOrThrow(id);
        navLinkRepository.delete(navLink);
    }

    @Override
    public List<NavLinkResponse> getMine(User caller) {
        // nav_links only ever models the ADMIN portal's own sidebar (Prompt 21's NavLink
        // has no userType column - this table isn't multi-portal the way ElderSphere's is).
        // ELDER/FAMILY_MEMBER/CARETAKER's in-app navigation is hardcoded in their
        // frontend role route trees (Phase 4), not DB-driven at all: those three portals'
        // nav is small, fixed, and never needs admin-side editing, so paying for a
        // DB round trip plus RBAC filtering on every page load for a handful of static
        // links would be pure overhead with no corresponding benefit - unlike the admin
        // portal, whose nav genuinely varies by fine-grained Role/Permission and benefits
        // from being editable without a redeploy.
        if (caller.getUserType() != UserTypeEnum.SUPER_ADMIN && caller.getUserType() != UserTypeEnum.ADMIN) {
            return Collections.emptyList();
        }

        List<NavLink> activeLinks = navLinkRepository.findByStatusOrderByNavGroupAscNavIndexAsc(NavLinkStatusEnum.ACTIVE);

        boolean isSuperAdmin = caller.getUserType() == UserTypeEnum.SUPER_ADMIN;

        return activeLinks.stream()
                // SUPER_ADMIN sees the full list unfiltered - superAdminOnly rows included,
                // no permission filtering at all (AdminPermissionGuard would pass every
                // check for it anyway, since it always grants SUPER_ADMIN - this just skips
                // the pointless round trip through the guard for every row).
                .filter(link -> isSuperAdmin || !Boolean.TRUE.equals(link.getSuperAdminOnly()))
                .filter(link -> isSuperAdmin
                        || link.getRequiredPermission() == null
                        || adminPermissionGuard.has(link.getRequiredPermission()))
                .map(this::toResponse)
                .toList();
    }

    private NavLink findOrThrow(Long id) throws GenericException {
        return navLinkRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.DATA_NOT_FOUND, "Navigation link not found."));
    }

    private NavLinkResponse toResponse(NavLink navLink) {
        return NavLinkResponse.builder()
                .id(navLink.getId())
                .navGroup(navLink.getNavGroup())
                .navIndex(navLink.getNavIndex())
                .name(navLink.getName())
                .path(navLink.getPath())
                .icon(navLink.getIcon())
                .requiredPermission(navLink.getRequiredPermission())
                .superAdminOnly(navLink.getSuperAdminOnly())
                .status(navLink.getStatus())
                .createdAt(navLink.getCreatedAt())
                .updatedAt(navLink.getUpdatedAt())
                .build();
    }
}
