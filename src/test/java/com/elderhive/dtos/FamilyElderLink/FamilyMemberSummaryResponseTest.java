package com.elderhive.dtos.FamilyElderLink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** See NotificationResponseTest's own comment - same Lombok/Jackson "is"-prefix-stripped-twice
 *  gotcha, same @JsonProperty pin, same reason a real ObjectMapper round trip (not a mocked
 *  DTO) is the only thing that actually catches a regression here. */
class FamilyMemberSummaryResponseTest {

    @Test
    void serializesTheIsOwnerFieldUnderItsOwnNameNotOwner() throws Exception {
        FamilyMemberSummaryResponse response = FamilyMemberSummaryResponse.builder()
                .userId(1L).fullName("Robert").isOwner(true).build();

        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).contains("\"isOwner\":true");
        assertThat(json).doesNotContain("\"owner\":");
    }
}
