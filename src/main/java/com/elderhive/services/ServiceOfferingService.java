package com.elderhive.services;

import com.elderhive.dtos.ServiceOffering.ServiceOfferingRequest;
import com.elderhive.dtos.ServiceOffering.ServiceOfferingResponse;
import com.elderhive.exceptions.GenericException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ServiceOfferingService {

    ServiceOfferingResponse create(ServiceOfferingRequest request) throws GenericException;

    ServiceOfferingResponse update(Long id, ServiceOfferingRequest request) throws GenericException;

    ServiceOfferingResponse getById(Long id) throws GenericException;

    Page<ServiceOfferingResponse> getAll(Pageable pageable);

    void delete(Long id) throws GenericException;
}
