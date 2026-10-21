package com.elderhive.repositories;

import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.CaretakerVerificationDocument;
import com.elderhive.entities.FileAsset;
import com.elderhive.entities.User;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.DocumentReviewStatusEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres - confirms V21's migration matches both entity
 * mappings, the FK cascades (CaretakerProfile -> documents, FileAsset -> documents), and
 * JPA auditing.
 */
@SpringBootTest
@Transactional
class CaretakerVerificationDocumentRepositoryTest {

    @Autowired
    private CaretakerVerificationDocumentRepository documentRepository;

    @Autowired
    private FileAssetRepository fileAssetRepository;

    @Autowired
    private CaretakerProfileRepository caretakerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.CARETAKER).status(UserStatusEnum.ACTIVE).build());
    }

    private CaretakerProfile persistProfile(Long userId) {
        return caretakerProfileRepository.save(CaretakerProfile.builder()
                .userId(userId).verificationStatus(CaretakerVerificationStatusEnum.PENDING).build());
    }

    private FileAsset persistFileAsset(Long uploadedBy) {
        return fileAssetRepository.save(FileAsset.builder()
                .storageKey("2026/10/uuid.pdf").originalFilename("id.pdf").contentType("application/pdf")
                .size(1024L).uploadedByUserId(uploadedBy).build());
    }

    @Test
    void persistingAndReloadingADocumentPopulatesAuditFieldsAndEveryColumn() {
        User caretaker = persistUser("cvd-roundtrip@example.com");
        CaretakerProfile profile = persistProfile(caretaker.getId());
        FileAsset asset = persistFileAsset(caretaker.getId());

        CaretakerVerificationDocument saved = documentRepository.save(CaretakerVerificationDocument.builder()
                .caretakerId(profile.getId()).fileAssetId(asset.getId()).docType("ID_PROOF")
                .reviewStatus(DocumentReviewStatusEnum.PENDING).build());
        entityManager.flush();
        entityManager.clear();

        CaretakerVerificationDocument reloaded = documentRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCaretakerId()).isEqualTo(profile.getId());
        assertThat(reloaded.getFileAssetId()).isEqualTo(asset.getId());
        assertThat(reloaded.getDocType()).isEqualTo("ID_PROOF");
        assertThat(reloaded.getReviewStatus()).isEqualTo(DocumentReviewStatusEnum.PENDING);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByCaretakerIdOrderByCreatedAtDescReturnsOnlyThatCaretakersDocuments() {
        User caretaker1 = persistUser("cvd-find1@example.com");
        User caretaker2 = persistUser("cvd-find2@example.com");
        CaretakerProfile profile1 = persistProfile(caretaker1.getId());
        CaretakerProfile profile2 = persistProfile(caretaker2.getId());
        FileAsset asset1 = persistFileAsset(caretaker1.getId());
        FileAsset asset2 = persistFileAsset(caretaker2.getId());
        documentRepository.save(CaretakerVerificationDocument.builder()
                .caretakerId(profile1.getId()).fileAssetId(asset1.getId()).reviewStatus(DocumentReviewStatusEnum.PENDING).build());
        documentRepository.save(CaretakerVerificationDocument.builder()
                .caretakerId(profile2.getId()).fileAssetId(asset2.getId()).reviewStatus(DocumentReviewStatusEnum.PENDING).build());
        entityManager.flush();

        List<CaretakerVerificationDocument> docs = documentRepository.findByCaretakerIdOrderByCreatedAtDesc(profile1.getId());

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getCaretakerId()).isEqualTo(profile1.getId());
    }

    @Test
    void deletingTheCaretakerProfileCascadesToItsVerificationDocuments() {
        User caretaker = persistUser("cvd-cascade-profile@example.com");
        CaretakerProfile profile = persistProfile(caretaker.getId());
        FileAsset asset = persistFileAsset(caretaker.getId());
        CaretakerVerificationDocument saved = documentRepository.save(CaretakerVerificationDocument.builder()
                .caretakerId(profile.getId()).fileAssetId(asset.getId()).reviewStatus(DocumentReviewStatusEnum.PENDING).build());
        entityManager.flush();

        caretakerProfileRepository.delete(profile);
        entityManager.flush();
        entityManager.clear();

        assertThat(documentRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deletingTheFileAssetCascadesToItsVerificationDocument() {
        User caretaker = persistUser("cvd-cascade-asset@example.com");
        CaretakerProfile profile = persistProfile(caretaker.getId());
        FileAsset asset = persistFileAsset(caretaker.getId());
        CaretakerVerificationDocument saved = documentRepository.save(CaretakerVerificationDocument.builder()
                .caretakerId(profile.getId()).fileAssetId(asset.getId()).reviewStatus(DocumentReviewStatusEnum.PENDING).build());
        entityManager.flush();

        fileAssetRepository.delete(asset);
        entityManager.flush();
        entityManager.clear();

        assertThat(documentRepository.findById(saved.getId())).isEmpty();
    }
}
