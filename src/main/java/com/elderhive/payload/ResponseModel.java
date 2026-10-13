package com.elderhive.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseModel<T> {

    private String message;
    private String status;
    private T data;
    private String errorCode;

    public ResponseModel(String message, T data) {
        this.message = message;
        this.data = data;
    }

    public ResponseModel(String message, T data, String errorCode) {
        this.message = message;
        this.data = data;
        this.errorCode = errorCode;
    }

    public ResponseModel<T> toSuccess() {
        this.status = "SUCCESS";
        return this;
    }

    public ResponseModel<T> toFailure() {
        this.status = "FAILED";
        return this;
    }

    public ResponseModel<T> toInternalServerError() {
        this.status = "INTERNAL_SERVER_ERROR";
        return this;
    }
}
