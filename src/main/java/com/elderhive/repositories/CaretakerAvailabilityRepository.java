package com.elderhive.repositories;

import com.elderhive.entities.CaretakerAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaretakerAvailabilityRepository extends JpaRepository<CaretakerAvailability, Long> {

    List<CaretakerAvailability> findByCaretakerIdOrderByDayOfWeekAscStartTimeAsc(Long caretakerId);
}
