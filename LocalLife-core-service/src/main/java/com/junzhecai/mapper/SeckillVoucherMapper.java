package com.junzhecai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.junzhecai.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Param;

public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    Integer rollbackStock(@Param("voucherId") Long voucherId);

}
