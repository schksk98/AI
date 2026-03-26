package com.zhaopx.ai.bean.res;

import com.zhaopx.ai.enums.ResultCodeEnum;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * @Description 统一返回结果的类
 * @Author: ZhaoPengXiang
 * @Date: 2023-05-24 3:21 PM
 */
@Data
public class R<T> {

    /**
     * 返回码
     */
    private String bizCode;

    /**
     * 返回消息
     */
    private String bizDesc;

    /**
     * 返回数据
     */
    private T data;

    private R() {
    }

    /**
     * 响应成功
     * @author ZhaoPengXiang
     * @date 2023/5/24 3:42 PM
     **/
    public static <T> R<T> success() {
        R<T> r = new R<T>();
        r.setBizCode(ResultCodeEnum.SUCCESS.getCode());
        r.setBizDesc(ResultCodeEnum.SUCCESS.getMessage());
        r.setData(null);
        return r;
    }

    public static <T> R<T> success(String message) {
        R<T> r = new R<T>();
        r.setBizCode(ResultCodeEnum.SUCCESS.getCode());
        r.setBizDesc(StringUtils.isEmpty(message) ? ResultCodeEnum.SUCCESS.getMessage() : message);
        r.setData(null);
        return r;
    }

    public static <T> R<T> success(T data) {
        R<T> r = new R<T>();
        r.setBizCode(ResultCodeEnum.SUCCESS.getCode());
        r.setBizDesc(ResultCodeEnum.SUCCESS.getMessage());
        r.setData(data);
        return r;
    }

    public static <T> R<T> success(String message, T data) {
        R<T> r = new R<T>();
        r.setBizCode(ResultCodeEnum.SUCCESS.getCode());
        r.setBizDesc(StringUtils.isEmpty(message) ? ResultCodeEnum.SUCCESS.getMessage() : message);
        r.setData(data);
        return r;
    }

    public static <T> R<T> success(String code, String message, T data) {
        R<T> r = new R<T>();
        r.setBizCode(StringUtils.isBlank(code) ? ResultCodeEnum.SUCCESS.getCode() : code);
        r.setBizDesc(StringUtils.isEmpty(message) ? ResultCodeEnum.SUCCESS.getMessage() : message);
        r.setData(data);
        return r;
    }

    /**
     * 响应失败
     * @author ZhaoPengXiang
     * @date 2023/5/24 3:46 PM
     **/
    public static <T> R<T> fail() {
        R<T> r = new R<T>();
        r.setBizCode(ResultCodeEnum.FAIL.getCode());
        r.setBizDesc(ResultCodeEnum.FAIL.getMessage());
        r.setData(null);
        return r;
    }

    public static <T> R<T> fail(String message) {
        R<T> r = new R<T>();
        r.setBizCode(ResultCodeEnum.FAIL.getCode());
        r.setBizDesc(StringUtils.isEmpty(message) ? ResultCodeEnum.FAIL.getMessage() : message);
        r.setData(null);
        return r;
    }

    public static <T> R<T> fail(T data) {
        R<T> r = new R<T>();
        r.setBizCode(ResultCodeEnum.FAIL.getCode());
        r.setBizDesc(ResultCodeEnum.FAIL.getMessage());
        r.setData(data);
        return r;
    }

    public static <T> R<T> fail(String message, T data) {
        R<T> r = new R<T>();
        r.setBizCode(ResultCodeEnum.FAIL.getCode());
        r.setBizDesc(StringUtils.isEmpty(message) ? ResultCodeEnum.FAIL.getMessage() : message);
        r.setData(data);
        return r;
    }

    public static <T> R<T> fail(String code, String message, T data) {
        R<T> r = new R<T>();
        r.setBizCode(StringUtils.isBlank(code) ? ResultCodeEnum.FAIL.getCode() : code);
        r.setBizDesc(StringUtils.isEmpty(message) ? ResultCodeEnum.FAIL.getMessage() : message);
        r.setData(data);
        return r;
    }
}

