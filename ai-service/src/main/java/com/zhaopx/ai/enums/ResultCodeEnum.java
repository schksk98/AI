package com.zhaopx.ai.enums;

import lombok.Getter;

/**
 * @Description
 * @Author: ZhaoPengXiang
 * @Date: 2023-05-24 3:27 PM
 */
@Getter
public enum ResultCodeEnum {

    SUCCESS("00000", "成功"),
    FAIL("00001", "失败");


    private final String code;
    private final String message;

    ResultCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
