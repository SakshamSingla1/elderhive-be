package com.elderhive.services.impl;

import com.elderhive.dtos.ServiceOffering.ServiceOfferingRequest;
import com.elderhive.dtos.ServiceOffering.ServiceOfferingResponse;
import com.elderhive.entities.ServiceCategory;
import com.elderhive.entities.ServiceOffering;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.ServiceOfferingStatusEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ServiceCategoryRepository;
import com.elderhive.repositories.ServiceOfferingRepository;
import com.elderhive.services.ServiceOfferingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServiceOfferingServiceImpl implements ServiceOfferingService {

    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;

    @Override
    @Transactional
    public ServiceOfferingResponse create(ServiceOfferingRequest request) throws GenericException {
        assertCategoryExists(request.getCategoryId());

        ServiceOffering offering = ServiceOffering.builder()
                .name(request.getName())
                .categoryId(request.getCategoryId())
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .durationMinutes(request.getDurationMinutes())
                .status(request.getStatus() != null ? request.getStatus() : ServiceOfferingStatusEnum.ACTIVE)
                .build();
        return toResponse(serviceOfferingRepository.save(offering));
    }

    @Override
    @Transactional
    public ServiceOfferingResponse update(Long id, ServiceOfferingRequest request) throws GenericException {
        ServiceOffering offering = findOrThrow(id);
        assertCategoryExists(request.getCategoryId());

        offering.setName(request.getName());
        offering.setCategoryId(request.getCategoryId());
        offering.setDescription(request.getDescription());
        offering.setBasePrice(request.getBasePrice());
        offering.setDurationMinutes(request.getDurationMinutes());
        if (request.getStatus() != null) {
            offering.setStatus(request.getStatus());
        }
        return toResponse(serviceOfferingRepository.save(offering));
    }

    @Override
    public ServiceOfferingResponse getById(Long id) throws GenericException {
        return toResponse(findOrThrow(id));
    }

    @Override
    public Page<ServiceOfferingResponse> getAll(Long categoryId, ServiceOfferingStatusEnum status, Pageable pageable) {
        return serviceOfferingRepository.findByCriteria(categoryId, status, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public void delete(Long id) throws GenericException {
        ServiceOffering offering = findOrThrow(id);
        serviceOfferingRepository.delete(offering);
    }

    private ServiceOffering findOrThrow(Long id) throws GenericException {
        return serviceOfferingRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.SERVICE_OFFERING_NOT_FOUND, "Service offering not found"));
    }

    /** Fails cleanly with a 404 rather than letting an invalid categoryId surface as a raw
     *  FK-violation 500 from the database. */
    private void assertCategoryExists(Long categoryId) throws GenericException {
        if (!serviceCategoryRepository.existsById(categoryId)) {
            throw new GenericException(ExceptionCodeEnum.SERVICE_CATEGORY_NOT_FOUND, "Service category not found");
        }
    }

    private ServiceOfferingResponse toResponse(ServiceOffering offering) {
        String categoryName = serviceCategoryRepository.findById(offering.getCategoryId())
                .map(ServiceCategory::getName)
                .orElse(null);
        return ServiceOfferingResponse.builder()
                .id(offering.getId())
                .name(offering.getName())
                .categoryId(offering.getCategoryId())
                .categoryName(categoryName)
                .description(offering.getDescription())
                .basePrice(offering.getBasePrice())
                .durationMinutes(offering.getDurationMinutes())
                .status(offering.getStatus())
                .createdAt(offering.getCreatedAt())
                .updatedAt(offering.getUpdatedAt())
                .build();
    }
}
