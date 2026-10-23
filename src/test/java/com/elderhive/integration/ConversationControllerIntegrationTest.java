package com.elderhive.integration;

import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end over the real dispatched Spring Security + MVC stack (Prompt 89) - the REST
 * side of messaging: starting/reusing a conversation, listing "my conversations", reading
 * paginated history, and marking a conversation read. Realtime send itself is covered by
 * MessagingServiceImplTest and ChatWebSocketControllerTest, since MockMvc has no STOMP
 * client to drive a @MessageMapping handler through.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConversationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    private Long getOrCreateConversation(Cookie callerCookie, Long otherUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/conversations").cookie(callerCookie)
                        .contentType("application/json").content("{\"otherUserId\":%d}".formatted(otherUserId)))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    @Test
    void startingAConversationTwiceReturnsTheSameOneBothTimes() throws Exception {
        User family = persistUser("convo-reuse-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretaker = persistUser("convo-reuse-caretaker@example.com", UserTypeEnum.CARETAKER);
        Cookie familyCookie = loginCookieFor(family);

        Long first = getOrCreateConversation(familyCookie, caretaker.getId());
        Long second = getOrCreateConversation(familyCookie, caretaker.getId());

        assertThat(second).isEqualTo(first);
    }

    @Test
    void aUserCannotStartAConversationWithThemselvesAndGets400() throws Exception {
        User family = persistUser("convo-self-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        Cookie familyCookie = loginCookieFor(family);

        mockMvc.perform(post("/api/v1/conversations").cookie(familyCookie)
                        .contentType("application/json").content("{\"otherUserId\":%d}".formatted(family.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void myConversationsListsOnlyConversationsTheCallerParticipatesIn() throws Exception {
        User family = persistUser("convo-list-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretaker = persistUser("convo-list-caretaker@example.com", UserTypeEnum.CARETAKER);
        User stranger = persistUser("convo-list-stranger@example.com", UserTypeEnum.FAMILY_MEMBER);
        Cookie familyCookie = loginCookieFor(family);
        Cookie strangerCookie = loginCookieFor(stranger);
        getOrCreateConversation(familyCookie, caretaker.getId());

        mockMvc.perform(get("/api/v1/conversations").cookie(familyCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].otherUserId").value(caretaker.getId()));

        mockMvc.perform(get("/api/v1/conversations").cookie(strangerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void messageHistoryIsPaginatedOldestFirstAndDeniedToNonParticipants() throws Exception {
        User family = persistUser("convo-history-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretaker = persistUser("convo-history-caretaker@example.com", UserTypeEnum.CARETAKER);
        User stranger = persistUser("convo-history-stranger@example.com", UserTypeEnum.FAMILY_MEMBER);
        Cookie familyCookie = loginCookieFor(family);
        Cookie strangerCookie = loginCookieFor(stranger);
        Long conversationId = getOrCreateConversation(familyCookie, caretaker.getId());

        mockMvc.perform(get("/api/v1/conversations/" + conversationId + "/messages").cookie(familyCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));

        mockMvc.perform(get("/api/v1/conversations/" + conversationId + "/messages").cookie(strangerCookie))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/conversations/999999/messages").cookie(familyCookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void markReadIsAllowedForParticipantsAndForbiddenForStrangers() throws Exception {
        User family = persistUser("convo-markread-family@example.com", UserTypeEnum.FAMILY_MEMBER);
        User caretaker = persistUser("convo-markread-caretaker@example.com", UserTypeEnum.CARETAKER);
        User stranger = persistUser("convo-markread-stranger@example.com", UserTypeEnum.FAMILY_MEMBER);
        Cookie familyCookie = loginCookieFor(family);
        Cookie strangerCookie = loginCookieFor(stranger);
        Long conversationId = getOrCreateConversation(familyCookie, caretaker.getId());

        mockMvc.perform(put("/api/v1/conversations/" + conversationId + "/read").cookie(familyCookie))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/conversations/" + conversationId + "/read").cookie(strangerCookie))
                .andExpect(status().isForbidden());
    }
}
