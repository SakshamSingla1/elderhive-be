package com.elderhive.exceptions;

import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.payload.ResponseModel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    static Stream<Arguments> oneCodePerBucket() {
        return Stream.of(
                Arguments.of(ExceptionCodeEnum.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
                Arguments.of(ExceptionCodeEnum.VALIDATION_FAILED, HttpStatus.BAD_REQUEST),
                Arguments.of(ExceptionCodeEnum.CONFLICT, HttpStatus.CONFLICT),
                Arguments.of(ExceptionCodeEnum.USER_NOT_FOUND, HttpStatus.NOT_FOUND)
        );
    }

    @ParameterizedTest
    @MethodSource("oneCodePerBucket")
    void resolvesEachBucketToItsHttpStatus(ExceptionCodeEnum code, HttpStatus expectedStatus) {
        GenericException ex = new GenericException(code, "boom");

        ResponseEntity<ResponseModel<Void>> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(code.getValue());
        assertThat(response.getBody().getStatus()).isEqualTo("FAILED");
    }

    @org.junit.jupiter.api.Test
    void unrecognizedCodeFallsBackToInternalServerError() {
        GenericException ex = new GenericException(ExceptionCodeEnum.NULL_POINTER, "boom");

        ResponseEntity<ResponseModel<Void>> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
