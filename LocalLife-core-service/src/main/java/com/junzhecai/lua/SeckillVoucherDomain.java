package com.junzhecai.lua;

import lombok.Data;

//lua秒杀返回数据
@Data
public class SeckillVoucherDomain {

    //秒杀状态码
    private Integer code;

    //秒杀前库存
    private Integer beforeQty;

    //秒杀数量
    private Integer deductQty;

    //秒杀后库存
    private Integer afterQty;

}

