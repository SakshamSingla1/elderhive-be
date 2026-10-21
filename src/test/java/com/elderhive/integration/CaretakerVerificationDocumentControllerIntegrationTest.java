package com.elderhive.integration;

import com.elderhive.entities.Role;
import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.RoleRepository;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack, real local-disk file
 * storage (no Cloudinary credentials in the test environment, so FileStorageServiceImpl
 * falls through to its local-disk branch - the same branch FileStorageServiceImplTest
 * exercises in isolation, verified here wired all the way through the real HTTP stack).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaretakerVerificationDocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistCaretaker(String email) {
        return userRepository.save(User.builder()
                .email(email).fullName("Test " + email).passwordHash("hashed")
                .userType(UserTypeEnum.CARETAKER).status(UserStatusEnum.ACTIVE).build());
    }

    private User persistVerifyingAdmin(String email) {
        Role adminRole = roleRepository.findByName("Admin").orElseThrow();
        return userRepository.save(User.builder()
                .email(email).fullName("Test " + email).passwordHash("hashed")
                .userType(UserTypeEnum.ADMIN).roleId(adminRole.getId()).status(UserStatusEnum.ACTIVE).build());
    }

    private Long createCaretakerProfile(Cookie cookie) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/caretaker-profiles").cookie(cookie)
                        .contentType("application/json").content("{\"bio\":\"Bio\"}"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    @Test
    void aCaretakerCanUploadAVerificationDocumentAndSeeItInTheirOwnList() throws Exception {
        User caretaker = persistCaretaker("cvd-upload@example.com");
        Cookie cookie = loginCookieFor(caretaker);
        Long profileId = createCaretakerProfile(cookie);

        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "fake-bytes".getBytes());
        mockMvc.perform(multipart("/api/v1/caretaker-profiles/me/verification-documents").file(file)
                        .param("docType", "ID_PROOF").cookie(cookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.caretakerId").value(profileId))
                .andExpect(jsonPath("$.data.docType").value("ID_PROOF"))
                .andExpect(jsonPath("$.data.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.fileUrl").isNotEmpty());

        mockMvc.perform(get("/api/v1/caretaker-profiles/" + profileId + "/verification-documents").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].docType").value("ID_PROOF"));
    }

    @Test
    void uploadingWithNoCaretakerProfileYetReturns404() throws Exception {
        User caretaker = persistCaretaker("cvd-noprofile@example.com");
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/caretaker-profiles/me/verification-documents").file(file)
                        .cookie(loginCookieFor(caretaker)))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadingADisallowedFileTypeReturns400() throws Exception {
        User caretaker = persistCaretaker("cvd-badtype@example.com");
        Cookie cookie = loginCookieFor(caretaker);
        createCaretakerProfile(cookie);
        MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/x-msdownload", "bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/caretaker-profiles/me/verification-documents").file(file).cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aStrangerCannotListAnotherCaretakersDocumentsButAVerifyingAdminCan() throws Exception {
        User caretaker = persistCaretaker("cvd-access-caretaker@example.com");
        User stranger = persistCaretaker("cvd-access-stranger@example.com");
        User admin = persistVerifyingAdmin("cvd-access-admin@example.com");
        Cookie caretakerCookie = loginCookieFor(caretaker);
        Long profileId = createCaretakerProfile(caretakerCookie);
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "bytes".getBytes());
        mockMvc.perform(multipart("/api/v1/caretaker-profiles/me/verification-documents").file(file).cookie(caretakerCookie));

        mockMvc.perform(get("/api/v1/caretaker-profiles/" + profileId + "/verification-documents").cookie(loginCookieFor(stranger)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/caretaker-profiles/" + profileId + "/verification-documents").cookie(loginCookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void aVerifyingAdminCanReviewADocumentButAPlainCaretakerCannot() throws Exception {
        User caretaker = persistCaretaker("cvd-review-caretaker@example.com");
        User admin = persistVerifyingAdmin("cvd-review-admin@example.com");
        Cookie caretakerCookie = loginCookieFor(caretaker);
        createCaretakerProfile(caretakerCookie);
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "bytes".getBytes());
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/caretaker-profiles/me/verification-documents")
                        .file(file).cookie(caretakerCookie))
                .andReturn();
        Long docId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(put("/api/v1/caretaker-verification-documents/" + docId + "/review").cookie(caretakerCookie)
                        .contentType("application/json").content("{\"reviewStatus\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/caretaker-verification-documents/" + docId + "/review").cookie(loginCookieFor(admin))
                        .contentType("application/json").content("{\"reviewStatus\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"));
    }

    @Test
    void theUploaderCanDownloadTheirOwnFileAndAStrangerCannot() throws Exception {
        User caretaker = persistCaretaker("cvd-download-caretaker@example.com");
        User stranger = persistCaretaker("cvd-download-stranger@example.com");
        Cookie caretakerCookie = loginCookieFor(caretaker);
        createCaretakerProfile(caretakerCookie);
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "bytes".getBytes());
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/caretaker-profiles/me/verification-documents")
                        .file(file).cookie(caretakerCookie))
                .andReturn();
        JsonNode data = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data");
        Long fileAssetId = data.get("fileAssetId").asLong();

        mockMvc.perform(get("/api/v1/files/" + fileAssetId + "/download").cookie(caretakerCookie))
                .andExpect(status().isFound())
                .andExpect(header().exists("Location"));

        mockMvc.perform(get("/api/v1/files/" + fileAssetId + "/download").cookie(loginCookieFor(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/caretaker-profiles/1/verification-documents")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/files/1/download")).andExpect(status().isUnauthorized());
    }

    @Test
    void reviewingAnUnknownDocumentReturns404() throws Exception {
        User admin = persistVerifyingAdmin("cvd-review-404-admin@example.com");

        mockMvc.perform(put("/api/v1/caretaker-verification-documents/999999999/review").cookie(loginCookieFor(admin))
                        .contentType("application/json").content("{\"reviewStatus\":\"APPROVED\"}"))
                .andExpect(status().isNotFound());
    }
}
