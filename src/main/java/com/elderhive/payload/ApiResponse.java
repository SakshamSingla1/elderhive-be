package com.elderhive.payload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.TimeUnit;

@Slf4j
public class ApiResponse {
    public static final String SUCCESS = "Success";
    public static final String FAILED = "Failed";

    public static <T> ResponseEntity<ResponseModel<T>> createSuccess(T data, String successMessage) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ResponseModel<T>(successMessage, data).toSuccess());
    }

    public static <T> ResponseEntity<ResponseModel<T>> deleteSuccess(T data, String successMessage) {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(
                new ResponseModel<T>(successMessage, data).toSuccess());
    }

    public static <T> ResponseEntity<ResponseModel<T>> successResponse(T data, String successMessage) {
        return ResponseEntity.ok(new ResponseModel<T>(successMessage, data).toSuccess());
    }

    public static <T> ResponseEntity<ResponseModel<T>> successResponse(T data, String successMessage, int cacheTtlSeconds) {
        log.info("Cache control enabled for response");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(cacheTtlSeconds, TimeUnit.SECONDS).cachePrivate())
                .body(new ResponseModel<T>(successMessage, data).toSuccess());
    }

    public static <T> ResponseEntity<ResponseModel<T>> successResponse(T data) {
        return ResponseEntity.ok(new ResponseModel<T>(SUCCESS, data).toSuccess());
    }

    public static ResponseEntity<ResponseModel<Void>> successResponse() {
        return ResponseEntity.ok(new ResponseModel<Void>(SUCCESS, null).toSuccess());
    }

    public static <T> ResponseEntity<ResponseModel<T>> failureResponse(T data, String failureMessage) {
        return ResponseEntity.badRequest().body(
                new ResponseModel<T>(failureMessage, data).toFailure());
    }

    public static <T> ResponseEntity<ResponseModel<T>> failureResponse(T data, String failureMessage, String errorCode) {
        return ResponseEntity.badRequest().body(
                new ResponseModel<T>(failureMessage, data, errorCode).toFailure());
    }

    public static <T> ResponseEntity<ResponseModel<T>> exceptionResponse(String failureMessage) {
        return ResponseEntity.internalServerError().body(
                new ResponseModel<T>(failureMessage, null).toInternalServerError());
    }

    public static <T> ResponseEntity<ResponseModel<T>> exceptionResponse(String failureMessage, String errorCode) {
        return ResponseEntity.internalServerError().body(
                new ResponseModel<T>(failureMessage, null, errorCode).toInternalServerError());
    }

    public static <T> ResponseEntity<ResponseModel<T>> respond(T data, String successMessage, String errorMessage) {
        if (data != null && !data.equals(false)) {
            return ApiResponse.successResponse(data, successMessage);
        } else {
            return ApiResponse.failureResponse(data, errorMessage);
        }
    }

    public static <T> ResponseEntity<ResponseModel<T>> respond(T data, String successMessage, String errorMessage, boolean isFailure) {
        if (!isFailure) {
            return ApiResponse.successResponse(data, successMessage);
        } else {
            return ApiResponse.failureResponse(data, errorMessage);
        }
    }

    public static <T> ResponseEntity<ResponseModel<T>> respond(T data, String successMsg, String failureMsg, int cacheTtlSeconds) {
        if (data != null && !data.equals(false)) {
            return ApiResponse.successResponse(data, successMsg, cacheTtlSeconds);
        } else {
            return ApiResponse.failureResponse(data, failureMsg);
        }
    }

    public static <T> ResponseEntity<ResponseModel<T>> successResponse(T data, HttpStatus status) {
        return ResponseEntity.status(status).body(new ResponseModel<T>(status.name(), data).toSuccess());
    }
}
