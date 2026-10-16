package com.elderhive.dtos.Notification;

import com.elderhive.enums.NotificationTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A real Jackson serialization check, not just a Lombok getter check - Lombok's isRead()
 * getter for this already-"is"-prefixed field is correct, but Jackson's own bean-property
 * naming strips that "is" prefix AGAIN during serialization (producing "read", not "isRead")
 * unless pinned via @JsonProperty on the getter, as this class's own field does. A
 * service-layer test mocking this DTO directly would never catch a regression here - only
 * an actual ObjectMapper round trip does.
 */
class NotificationResponseTest {

    @Test
    void serializesTheIsReadFieldUnderItsOwnNameNotRead() throws Exception {
        NotificationResponse response = NotificationResponse.builder()
                .id(1L).type(NotificationTypeEnum.SYSTEM).title("Title").message("Message").isRead(true).build();

        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).contains("\"isRead\":true");
        assertThat(json).doesNotContain("\"read\":");
    }
}
