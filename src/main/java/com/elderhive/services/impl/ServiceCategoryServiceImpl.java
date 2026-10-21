package com.elderhive.services.impl;

import com.elderhive.dtos.ServiceCategory.ServiceCategoryRequest;
import com.elderhive.dtos.ServiceCategory.ServiceCategoryResponse;
import com.elderhive.entities.ServiceCategory;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.ServiceCategoryStatusEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ServiceCategoryRepository;
import com.elderhive.services.ServiceCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ServiceCategoryServiceImpl implements ServiceCategoryService {

    private final ServiceCategoryRepository serviceCategoryRepository;

    @Override
    @Transactional
    public ServiceCategoryResponse create(ServiceCategoryRequest request) throws GenericException {
        assertNameAvailable(request.getName(), null);

        ServiceCategory category = ServiceCategory.builder()
                .name(request.getName())
                .description(request.getDescription())
                .icon(request.getIcon())
                .status(request.getStatus() != null ? request.getStatus() : ServiceCategoryStatusEnum.ACTIVE)
                .build();
        return toResponse(serviceCategoryRepository.save(category));
    }

    @Override
    @Transactional
    public ServiceCategoryResponse update(Long id, ServiceCategoryRequest request) throws GenericException {
        ServiceCategory category = findOrThrow(id);
        assertNameAvailable(request.getName(), id);

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setIcon(request.getIcon());
        if (request.getStatus() != null) {
            category.setStatus(request.getStatus());
        }
        return toResponse(serviceCategoryRepository.save(category));
    }

    @Override
    public ServiceCategoryResponse getById(Long id) throws GenericException {
        return toResponse(findOrThrow(id));
    }

    @Override
    public Page<ServiceCategoryResponse> getAll(ServiceCategoryStatusEnum status, Pageable pageable) {
        return serviceCategoryRepository.findByCriteria(status, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public void delete(Long id) throws GenericException {
        ServiceCategory category = findOrThrow(id);
        serviceCategoryRepository.delete(category);
    }

    private ServiceCategory findOrThrow(Long id) throws GenericException {
        return serviceCategoryRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.SERVICE_CATEGORY_NOT_FOUND, "Service category not found"));
    }

    /** excludeId is null on create (no existing row to exempt); on update, a category keeping
     *  its own current name must not be flagged as colliding with itself. */
    private void assertNameAvailable(String name, Long excludeId) throws GenericException {
        Optional<ServiceCategory> existing = serviceCategoryRepository.findByName(name);
        if (existing.isPresent() && !Objects.equals(existing.get().getId(), excludeId)) {
            throw new GenericException(ExceptionCodeEnum.SERVICE_CATEGORY_ALREADY_EXISTS,
                    "A service category named '" + name + "' already exists");
        }
    }

    private ServiceCategoryResponse toResponse(ServiceCategory category) {
        return ServiceCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .icon(category.getIcon())
                .status(category.getStatus())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
