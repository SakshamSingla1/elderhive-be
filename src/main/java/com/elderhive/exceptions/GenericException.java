package com.elderhive.exceptions;

import com.elderhive.enums.ExceptionCodeEnum;
import lombok.*;

@RequiredArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
@Data
public class GenericException extends Exception {
    public ExceptionCodeEnum exceptionCode;
    public String errorMessage;
    public String referenceId;

    public GenericException(ExceptionCodeEnum exceptionCode, String errorMessage) {
        super(errorMessage);
        this.exceptionCode = exceptionCode;
        this.errorMessage = errorMessage;
    }
}
