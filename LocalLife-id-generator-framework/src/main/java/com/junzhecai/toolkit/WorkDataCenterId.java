package com.junzhecai.toolkit;

import lombok.Data;

@Data
public class WorkDataCenterId {
    // 工作机器ID(0~31)
    private Long workId;
    // 数据中心ID(0~31)
    private Long dataCenterId;
}
