package com.whm.starbucks.pojo.vo;

import lombok.Data;

import java.util.List;

// 客制化组
@Data
public class CustomizationGroupVO {
    private Long groupId;
    private String groupName;
    private Boolean isRequired;
    private Boolean isMultiple;
    private List<CustomizationOptionVO> options;
}