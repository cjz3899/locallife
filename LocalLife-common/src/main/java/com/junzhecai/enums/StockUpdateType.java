package com.junzhecai.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum StockUpdateType {
    DECREASE(-1, "扣减"),
    INCREASE(1, "增加");

    private final Integer code;
    private final String msg;

    private static final Map<Integer, StockUpdateType> CODE_MAP = new HashMap<>();

    static {
        for (StockUpdateType type : values()) {
            CODE_MAP.put(type.code, type);
        }
    }

    StockUpdateType(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    // O(1) 查找 + null 安全
    public static String getMsg(Integer code) {
        if (code == null) return "";
        StockUpdateType type = CODE_MAP.get(code);
        return type == null ? "" : type.msg;
    }

    public static StockUpdateType fromCode(Integer code) {
        if (code == null) return null;
        return CODE_MAP.get(code);
    }
}