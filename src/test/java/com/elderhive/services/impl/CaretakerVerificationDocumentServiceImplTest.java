package com.elderhive.services.impl;

import com.elderhive.dtos.CaretakerVerificationDocument.CaretakerVerificationDocumentResponse;
import com.elderhive.dtos.File.FileAssetResponse;
import com.elderhive.entities.CaretakerProfile;
import com.elderhive.entities.CaretakerVerificationDocument;
import com.elderhive.entities.User;
import com.elderhive.enums.CaretakerVerificationStatusEnum;
import com.elderhive.enums.DocumentReviewStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.CaretakerProfileRepository;
import com.elderhive.repositories.CaretakerVerificationDocumentRepository;
import com.elderhive.services.FileAssetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaretakerVerificationDocumentServiceImplTest {

    @Mock
    private CaretakerVerificationDocumentRepository documentRepository;

    @Mock
    private CaretakerProfileRepository caretakerProfileRepository;

    @Mock
    private FileAssetService fileAssetService;

    @InjectMocks
    private CaretakerVerificationDocumentServiceImpl service;

    private User user(Long id, UserTypeEnum type) {
        return User.builder().id(id).email("user" + id + "@example.com").fullName("User " + id)
                .passwordHash("hashed").userType(type).status(UserStatusEnum.ACTIVE).build();
    }

    private CaretakerProfile profile(Long id, Long userId) {
        return CaretakerProfile.builder().id(id).userId(userId).verificationStatus(CaretakerVerificationStatusEnum.PENDING).build();
    }

    @Test
    void uploadingForACaretakerWithAnExistingProfileCreatesAPendingDocument() throws GenericException {
        User caretaker = user(10L, UserTypeEnum.CARETAKER);
        when(caretakerProfileRepository.findByUserId(10L)).thenReturn(Optional.of(profile(100L, 10L)));
        when(fileAssetService.upload(any(), eq("caretaker-verification-documents"), eq(10L)))
                .thenReturn(FileAssetResponse.builder().id(500L).url("http://x/file.pdf").originalFilename("id.pdf").build());
        when(documentRepository.save(any())).thenAnswer(inv -> {
            CaretakerVerificationDocument d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "bytes".getBytes());

        CaretakerVerificationDocumentResponse response = service.uploadForCaller(caretaker, file, "ID_PROOF");

        assertThat(response.getCaretakerId()).isEqualTo(100L);
        assertThat(response.getFileAssetId()).isEqualTo(500L);
        assertThat(response.getDocType()).isEqualTo("ID_PROOF");
        assertThat(response.getReviewStatus()).isEqualTo(DocumentReviewStatusEnum.PENDING);
        assertThat(response.getFileUrl()).isEqualTo("http://x/file.pdf");
    }

    @Test
    void uploadingWithNoExistingCaretakerProfileThrowsCaretakerProfileNotFound() {
        User caretaker = user(10L, UserTypeEnum.CARETAKER);
        when(caretakerProfileRepository.findByUserId(10L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "bytes".getBytes());

        assertThatThrownBy(() -> service.uploadForCaller(caretaker, file, "ID_PROOF"))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND);
    }

    @Test
    void theOwningCaretakerCanListTheirOwnDocuments() throws GenericException {
        User caretaker = user(10L, UserTypeEnum.CARETAKER);
        when(caretakerProfileRepository.findById(100L)).thenReturn(Optional.of(profile(100L, 10L)));
        when(documentRepository.findByCaretakerIdOrderByCreatedAtDesc(100L)).thenReturn(List.of(
                CaretakerVerificationDocument.builder().id(1L).caretakerId(100L).fileAssetId(500L)
                        .docType("ID_PROOF").reviewStatus(DocumentReviewStatusEnum.PENDING).build()
        ));
        when(fileAssetService.getById(500L)).thenReturn(FileAssetResponse.builder().id(500L).url("http://x/file.pdf").build());

        List<CaretakerVerificationDocumentResponse> result = service.listForCaretaker(100L, caretaker);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFileUrl()).isEqualTo("http://x/file.pdf");
    }

    @Test
    void anAdminCanListAnyCaretakersDocuments() throws GenericException {
        User admin = user(99L, UserTypeEnum.SUPER_ADMIN);
        when(caretakerProfileRepository.findById(100L)).thenReturn(Optional.of(profile(100L, 10L)));
        when(documentRepository.findByCaretakerIdOrderByCreatedAtDesc(100L)).thenReturn(List.of());

        List<CaretakerVerificationDocumentResponse> result = service.listForCaretaker(100L, admin);

        assertThat(result).isEmpty();
    }

    @Test
    void aStrangerCannotListAnotherCaretakersDocuments() {
        User stranger = user(20L, UserTypeEnum.CARETAKER);
        when(caretakerProfileRepository.findById(100L)).thenReturn(Optional.of(profile(100L, 10L)));

        assertThatThrownBy(() -> service.listForCaretaker(100L, stranger))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.FORBIDDEN);
    }

    @Test
    void listingForAnUnknownCaretakerThrowsNotFound() {
        User caretaker = user(10L, UserTypeEnum.CARETAKER);
        when(caretakerProfileRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listForCaretaker(999L, caretaker))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND);
    }

    @Test
    void reviewingAKnownDocumentUpdatesItsStatus() throws GenericException {
        CaretakerVerificationDocument doc = CaretakerVerificationDocument.builder()
                .id(1L).caretakerId(100L).fileAssetId(500L).docType("ID_PROOF").reviewStatus(DocumentReviewStatusEnum.PENDING).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(caretakerProfileRepository.findById(100L)).thenReturn(Optional.of(profile(100L, 10L)));
        when(caretakerProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileAssetService.getById(500L)).thenReturn(FileAssetResponse.builder().id(500L).url("http://x/file.pdf").build());

        CaretakerVerificationDocumentResponse response = service.review(1L, DocumentReviewStatusEnum.APPROVED);

        assertThat(response.getReviewStatus()).isEqualTo(DocumentReviewStatusEnum.APPROVED);
    }

    @Test
    void approvingADocumentMarksTheOwningCaretakerProfileAsVerified() throws GenericException {
        CaretakerVerificationDocument doc = CaretakerVerificationDocument.builder()
                .id(1L).caretakerId(100L).fileAssetId(500L).docType("ID_PROOF").reviewStatus(DocumentReviewStatusEnum.PENDING).build();
        CaretakerProfile profile = profile(100L, 10L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(caretakerProfileRepository.findById(100L)).thenReturn(Optional.of(profile));
        when(caretakerProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileAssetService.getById(500L)).thenReturn(FileAssetResponse.builder().id(500L).url("http://x/file.pdf").build());

        service.review(1L, DocumentReviewStatusEnum.APPROVED);

        assertThat(profile.getVerificationStatus()).isEqualTo(CaretakerVerificationStatusEnum.VERIFIED);
    }

    @Test
    void rejectingADocumentMarksTheOwningCaretakerProfileAsRejected() throws GenericException {
        CaretakerVerificationDocument doc = CaretakerVerificationDocument.builder()
                .id(1L).caretakerId(100L).fileAssetId(500L).docType("ID_PROOF").reviewStatus(DocumentReviewStatusEnum.PENDING).build();
        CaretakerProfile profile = profile(100L, 10L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(caretakerProfileRepository.findById(100L)).thenReturn(Optional.of(profile));
        when(caretakerProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileAssetService.getById(500L)).thenReturn(FileAssetResponse.builder().id(500L).url("http://x/file.pdf").build());

        service.review(1L, DocumentReviewStatusEnum.REJECTED);

        assertThat(profile.getVerificationStatus()).isEqualTo(CaretakerVerificationStatusEnum.REJECTED);
    }

    @Test
    void reviewingAnUnknownDocumentThrowsNotFound() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.review(999L, DocumentReviewStatusEnum.APPROVED))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_VERIFICATION_DOCUMENT_NOT_FOUND);
    }

    @Test
    void reviewingADocumentWhoseCaretakerProfileNoLongerExistsThrowsNotFound() {
        CaretakerVerificationDocument doc = CaretakerVerificationDocument.builder()
                .id(1L).caretakerId(100L).fileAssetId(500L).docType("ID_PROOF").reviewStatus(DocumentReviewStatusEnum.PENDING).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(caretakerProfileRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.review(1L, DocumentReviewStatusEnum.APPROVED))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CARETAKER_PROFILE_NOT_FOUND);
    }
}
