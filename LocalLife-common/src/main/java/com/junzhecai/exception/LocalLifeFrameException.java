package com.junzhecai.exception;


import com.junzhecai.enums.BaseCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class LocalLifeFrameException extends BaseException {

    private Integer code;

    private String message;

    public LocalLifeFrameException() {
        super();
    }

    public LocalLifeFrameException(String message) {
        super(message);
    }

    public LocalLifeFrameException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public LocalLifeFrameException(BaseCode baseCode) {
        super(baseCode.getMsg());
        this.code = baseCode.getCode();
        this.message = baseCode.getMsg();
    }

    public LocalLifeFrameException(Throwable cause) {
        super(cause);
    }

    public LocalLifeFrameException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }
}
