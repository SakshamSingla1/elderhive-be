package com.elderhive.repositories;

import com.elderhive.entities.FileAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileAssetRepository extends JpaRepository<FileAsset, Long> {
}
