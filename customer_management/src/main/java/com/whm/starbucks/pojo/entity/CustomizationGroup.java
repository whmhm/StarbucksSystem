package com.whm.starbucks.pojo.entity;

import lombok.Data;

@Data
public class CustomizationGroup {
    private Long id;
    private String groupName;

    private Boolean isMultiple;
    private Integer sortOrder;
    private Integer isDeleted;

    private String gmtCreate; // '创建时间'

    private String gmtModified; // '修改时间'

}
