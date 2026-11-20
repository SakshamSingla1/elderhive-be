package com.elderhive.integration;

import com.elderhive.entities.User;
import com.elderhive.enums.UserStatusEnum;
import com.elderhive.enums.UserTypeEnum;
import com.elderhive.geocoding.GeocodingResult;
import com.elderhive.geocoding.GeocodingService;
import com.elderhive.repositories.UserRepository;
import com.elderhive.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GeocodingService is mocked (never a real Nominatim/Mapbox network call) - this test
 * covers the HTTP wiring (route, security, query-param binding, response shape), not the
 * provider implementations themselves (see GeocodingServiceSelectionTest/
 * MapboxGeocodingServiceTest/NominatimGeocodingServiceTest for those).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GeocodingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserRepository userRepository;
    @MockitoBean
    private GeocodingService geocodingService;

    private Cookie loginCookieFor(User user) {
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getUserType().name());
        return new Cookie("accessToken", token);
    }

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email).passwordHash("hashed").userType(UserTypeEnum.FAMILY_MEMBER).status(UserStatusEnum.ACTIVE).build());
    }

    @Test
    void searchRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/geocoding/search").param("query", "MG Road"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchReturnsTheGeocodingServicesResultsForAnAuthenticatedUser() throws Exception {
        User user = persistUser("geocoding-search@example.com");
        when(geocodingService.forwardGeocode(eq("MG Road")))
                .thenReturn(List.of(new GeocodingResult("MG Road, Bengaluru, India", 12.9716, 77.5946)));

        mockMvc.perform(get("/api/v1/geocoding/search").param("query", "MG Road").cookie(loginCookieFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].displayName").value("MG Road, Bengaluru, India"))
                .andExpect(jsonPath("$.data[0].latitude").value(12.9716))
                .andExpect(jsonPath("$.data[0].longitude").value(77.5946));
    }
}
