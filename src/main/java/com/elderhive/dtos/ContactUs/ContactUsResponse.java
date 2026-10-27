package com.elderhive.dtos.ContactUs;

import com.elderhive.enums.ContactUsStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactUsResponse {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private String message;
    private ContactUsStatusEnum status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
