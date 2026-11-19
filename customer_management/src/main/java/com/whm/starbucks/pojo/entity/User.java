package com.whm.starbucks.pojo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User {
    private Long id;

    private String nickname;

    /** 密码不可对外输出，避免接口把明文密码返回给客户端 */
    @JsonIgnore
    private String password;

    private String phone;

    private int isDeleted; // 0: not deleted, 1: deleted

    private int memberLevel;

    private int starBalance;

    private int cardBalance;

}