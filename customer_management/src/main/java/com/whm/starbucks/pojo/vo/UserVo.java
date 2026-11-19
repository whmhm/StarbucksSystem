package com.whm.starbucks.pojo.vo;

import lombok.Data;

@Data
public class UserVo {

    private String nickname;

    private String phone;

    private int memberLevel;

    private int starBalance;

    private int cardBalance;
}
