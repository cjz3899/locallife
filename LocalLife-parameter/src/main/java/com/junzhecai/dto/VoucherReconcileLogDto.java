package com.junzhecai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@AllArgsConstructor
public class VoucherReconcileLogDto implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;


    private Long orderId;

    private Long userId;

    private Long voucherId;

    private String messageId;

    private String detail;

    private Integer beforeQty;

    private Integer changeQty;

    private Integer afterQty;

    private Long traceId;

    private Integer logType;

    private Integer businessType;
}