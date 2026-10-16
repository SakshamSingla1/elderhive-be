package com.elderhive.config;

import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A real end-to-end check: boots the app on a random port, opens a genuine raw WebSocket
 * (SockJS also exposes a plain /ws/websocket endpoint) STOMP connection with a real
 * accessToken cookie, and proves the whole pipe - handshake auth, broker, and per-topic
 * subscription authorization - actually works together, not just each interceptor in
 * isolation. Deliberately NOT @Transactional: the server handles the handshake/STOMP frames
 * on its own thread against its own DB connection, which would never see an uncommitted
 * test-side transaction's rows - the test user is committed for real and cleaned up in
 * @AfterEach instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private User testUser;
    private User otherUser;

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.ELDER).status(UserStatusEnum.ACTIVE).build());
    }

    @AfterEach
    void cleanUp() {
        if (testUser != null) userRepository.deleteById(testUser.getId());
        if (otherUser != null) userRepository.deleteById(otherUser.getId());
    }

    private StompSession connect(String cookieValue) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "accessToken=" + cookieValue);

        CompletableFuture<StompSession> future = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws/websocket", headers, new StompSessionHandlerAdapter() {});
        return future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void aValidAccessTokenCookieConnectsAndCanSubscribeToItsOwnNotificationsTopicAndReceiveAPush() throws Exception {
        testUser = persistUser("ws-e2e-own-topic@example.com");
        String token = jwtUtil.generateAccessToken(testUser.getEmail(), testUser.getId(), testUser.getUserType().name());

        StompSession session = connect(token);
        assertThat(session.isConnected()).isTrue();

        CompletableFuture<String> received = new CompletableFuture<>();
        session.subscribe("/topic/notifications/" + testUser.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.complete((String) payload);
            }
        });

        // Give the broker a moment to register the subscription before the server pushes.
        Thread.sleep(300);
        messagingTemplate.convertAndSend("/topic/notifications/" + testUser.getId(), "hello " + testUser.getId());

        String payload = received.get(5, TimeUnit.SECONDS);
        assertThat(payload).isEqualTo("hello " + testUser.getId());

        session.disconnect();
    }

    @Test
    void aUserCannotSubscribeToAnotherUsersNotificationsTopicOverARealConnection() throws Exception {
        testUser = persistUser("ws-e2e-other-topic@example.com");
        otherUser = persistUser("ws-e2e-other-topic-victim@example.com");
        String token = jwtUtil.generateAccessToken(testUser.getEmail(), testUser.getId(), testUser.getUserType().name());

        CompletableFuture<Throwable> errorReceived = new CompletableFuture<>();
        StompSession session = connectWithErrorHandler(token, errorReceived);
        assertThat(session.isConnected()).isTrue();

        session.subscribe("/topic/notifications/" + otherUser.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                // never expected
            }
        });

        Throwable error = errorReceived.get(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();

        // The server itself closes the session once the interceptor rejects the
        // subscription (an ERROR frame followed by connection close) - nothing left to
        // disconnect here, and calling disconnect() on an already-closed session throws.
        if (session.isConnected()) {
            session.disconnect();
        }
    }

    private StompSession connectWithErrorHandler(String cookieValue, CompletableFuture<Throwable> errorReceived) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "accessToken=" + cookieValue);

        CompletableFuture<StompSession> future = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws/websocket", headers, new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(StompSession session, org.springframework.messaging.simp.stomp.StompCommand command,
                                                 StompHeaders headers, byte[] payload, Throwable exception) {
                        errorReceived.complete(exception);
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        errorReceived.complete(exception);
                    }
                });
        return future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void noAccessTokenCookieIsRejectedAtTheHandshake() {
        assertThatThrownBy(() -> {
            WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
            stompClient.setMessageConverter(new StringMessageConverter());
            CompletableFuture<StompSession> future = stompClient.connectAsync(
                    "ws://localhost:" + port + "/ws/websocket", new StompSessionHandlerAdapter() {});
            future.get(5, TimeUnit.SECONDS);
        }).isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }
}
