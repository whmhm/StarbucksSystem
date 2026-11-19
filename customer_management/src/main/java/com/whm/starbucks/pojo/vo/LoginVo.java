package com.whm.starbucks.pojo.vo;

import lombok.Data;

@Data
public class LoginVo {
    private String phone;

    private long userId;

    private String token;

    private long expireTime;

    private String refreshToken;

    private long refreshExpireTime;

}
