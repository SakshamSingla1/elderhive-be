package com.elderhive.security;

import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres and real JwtUtil - proves the whole
 * accessToken-cookie -> JWT -> handshake-attributes chain actually works, the same
 * end-to-end discipline JwtAuthFilterTest already applies to the REST auth path.
 */
@SpringBootTest
@Transactional
class WebSocketJwtHandshakeInterceptorTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    private WebSocketJwtHandshakeInterceptor interceptor() {
        return new WebSocketJwtHandshakeInterceptor(jwtUtil, userRepository);
    }

    private User persistUser(String email, UserTypeEnum userType) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(userType).status(UserStatusEnum.ACTIVE).build());
    }

    @Test
    void validAccessTokenCookiePopulatesHandshakeAttributesAndAllowsTheUpgrade() {
        User user = persistUser("ws-handshake-valid@example.com", UserTypeEnum.ELDER);
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setCookies(new Cookie("accessToken", token));
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean proceed = interceptor().beforeHandshake(
                new ServletServerHttpRequest(httpRequest), new ServletServerHttpResponse(httpResponse), null, attributes);

        assertThat(proceed).isTrue();
        assertThat(attributes.get(WebSocketJwtHandshakeInterceptor.ATTR_USER_ID)).isEqualTo(user.getId());
        assertThat(attributes.get(WebSocketJwtHandshakeInterceptor.ATTR_EMAIL)).isEqualTo(user.getEmail());
        assertThat(attributes.get(WebSocketJwtHandshakeInterceptor.ATTR_USER_TYPE)).isEqualTo("ELDER");
    }

    @Test
    void noAccessTokenCookieRejectsTheHandshakeWith401() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        boolean proceed = interceptor().beforeHandshake(
                new ServletServerHttpRequest(httpRequest), new ServletServerHttpResponse(httpResponse), null, new HashMap<>());

        assertThat(proceed).isFalse();
        assertThat(httpResponse.getStatus()).isEqualTo(401);
    }

    @Test
    void malformedTokenRejectsTheHandshake() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setCookies(new Cookie("accessToken", "not-a-real-jwt"));
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        boolean proceed = interceptor().beforeHandshake(
                new ServletServerHttpRequest(httpRequest), new ServletServerHttpResponse(httpResponse), null, new HashMap<>());

        assertThat(proceed).isFalse();
        assertThat(httpResponse.getStatus()).isEqualTo(401);
    }

    @Test
    void tokenForAUserThatNoLongerExistsRejectsTheHandshake() {
        String token = jwtUtil.generateAccessToken("ws-handshake-ghost@example.com", 999999L, "ELDER");

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setCookies(new Cookie("accessToken", token));
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        boolean proceed = interceptor().beforeHandshake(
                new ServletServerHttpRequest(httpRequest), new ServletServerHttpResponse(httpResponse), null, new HashMap<>());

        assertThat(proceed).isFalse();
        assertThat(httpResponse.getStatus()).isEqualTo(401);
    }
}
