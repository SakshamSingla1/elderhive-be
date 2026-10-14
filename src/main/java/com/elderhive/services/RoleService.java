package com.elderhive.services;

import com.elderhive.dtos.Role.RoleRequest;
import com.elderhive.dtos.Role.RoleResponse;
import com.elderhive.exceptions.GenericException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RoleService {

    RoleResponse create(RoleRequest request) throws GenericException;

    RoleResponse update(Long id, RoleRequest request) throws GenericException;

    RoleResponse getById(Long id) throws GenericException;

    Page<RoleResponse> getAll(Pageable pageable);

    void delete(Long id) throws GenericException;
}
