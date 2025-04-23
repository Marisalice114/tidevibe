package com.sky.service;

import com.alibaba.fastjson.JSONObject;
import com.sky.vo.OrderPaymentVO;

import java.math.BigDecimal;

/**
 * 支付服务接口
 */
public interface PaymentService {

    /**
     * 创建支付订单
     *
     * @param orderNum 订单号
     * @param amount 支付金额
     * @param description 商品描述
     * @param openid 用户openid
     * @return 支付信息
     */
    JSONObject createPayment(String orderNum, BigDecimal amount, String description, String openid) throws Exception;

    /**
     * 处理支付结果
     *
     * @param orderNum 订单号
     * @return 是否支付成功
     */
    boolean handlePaymentResult(String orderNum);

    /**
     * 将JSONObject转换为OrderPaymentVO
     *
     * @param jsonObject 支付结果
     * @return 支付VO对象
     */
    OrderPaymentVO convertToPaymentVO(JSONObject jsonObject);
}