package com.whm.starbucks.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseVo<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ResponseVo<T> success(T data) {
        return new ResponseVo<>(200, "success", data);
    }

    public static <T> ResponseVo<T> success(String message, T data) {
        return new ResponseVo<>(200, message, data);
    }

    public static <T> ResponseVo<T> fail(String message) {
        return new ResponseVo<>(500, message, null);
    }

    public static <T> ResponseVo<T> fail(int code, String message) {
        return new ResponseVo<>(code, message, null);
    }
}
