package com.elderhive.repositories;

import com.elderhive.entities.CaretakerVerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaretakerVerificationDocumentRepository extends JpaRepository<CaretakerVerificationDocument, Long> {

    List<CaretakerVerificationDocument> findByCaretakerIdOrderByCreatedAtDesc(Long caretakerId);
}
