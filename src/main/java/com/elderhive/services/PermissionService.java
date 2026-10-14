package com.elderhive.services;

import com.elderhive.dtos.Permission.PermissionResponse;
import com.elderhive.exceptions.GenericException;

import java.util.List;

public interface PermissionService {

    /** The full permission catalog — read-only, no creation here: the catalog is
     *  seeder-owned (AdminBootstrapSeeder, Prompt 24), never created through the API. */
    List<PermissionResponse> getAll();

    String assignToRole(Long roleId, Long permissionId) throws GenericException;

    String unassignFromRole(Long roleId, Long permissionId) throws GenericException;
}
