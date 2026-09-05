package com.junzhecai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@Builder
@TableName("tb_voucher_order_router")
public class VoucherOrderRouter implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private Long orderId;

    private Long userId;

    private Long voucherId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;


}
