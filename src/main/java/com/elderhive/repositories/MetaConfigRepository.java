package com.elderhive.repositories;

import com.elderhive.entities.MetaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MetaConfigRepository extends JpaRepository<MetaConfig, Long> {

    Optional<MetaConfig> findByContext(String context);

    List<MetaConfig> findAllByOrderByContextAsc();
}
