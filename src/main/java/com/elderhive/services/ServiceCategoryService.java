package com.elderhive.services;

import com.elderhive.dtos.ServiceCategory.ServiceCategoryRequest;
import com.elderhive.dtos.ServiceCategory.ServiceCategoryResponse;
import com.elderhive.exceptions.GenericException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ServiceCategoryService {

    ServiceCategoryResponse create(ServiceCategoryRequest request) throws GenericException;

    ServiceCategoryResponse update(Long id, ServiceCategoryRequest request) throws GenericException;

    ServiceCategoryResponse getById(Long id) throws GenericException;

    Page<ServiceCategoryResponse> getAll(Pageable pageable);

    void delete(Long id) throws GenericException;
}
