package com.elderhive.repositories;

import com.elderhive.entities.NavLink;
import com.elderhive.enums.NavLinkStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NavLinkRepository extends JpaRepository<NavLink, Long> {

    List<NavLink> findByStatusOrderByNavGroupAscNavIndexAsc(NavLinkStatusEnum status);
}
