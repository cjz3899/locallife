package com.junzhecai.lua;

import lombok.Data;

//lua秒杀返回数据
@Data
public class SeckillVoucherDomain {

    private Integer code;

    private Integer beforeQty;

    private Integer deductQty;

    private Integer afterQty;

}

