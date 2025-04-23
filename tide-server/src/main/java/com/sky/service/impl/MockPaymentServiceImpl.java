package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.sky.service.PaymentService;
import com.sky.vo.OrderPaymentVO;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 模拟支付服务实现
 */
@Service
@Profile({"dev", "test"})
public class MockPaymentServiceImpl implements PaymentService {

    @Override
    public JSONObject createPayment(String orderNum, BigDecimal amount, String description, String openid) {
        // 生成模拟的预支付ID和签名
        String prepayId = "mock_prepay_" + System.currentTimeMillis();
        String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr = RandomStringUtils.randomNumeric(32);

        // 构造返回数据
        JSONObject result = new JSONObject();
        result.put("timeStamp", timeStamp);
        result.put("nonceStr", nonceStr);
        result.put("package", "prepay_id=" + prepayId);
        result.put("signType", "RSA");
        result.put("paySign", "MOCK_SIGNATURE_" + timeStamp);

        // 添加模拟信息
        result.put("mock", true);
        result.put("orderNum", orderNum);
        result.put("total", amount);
        result.put("mockTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        return result;
    }

    @Override
    public boolean handlePaymentResult(String orderNum) {
        // 模拟支付成功
        return true;
    }

    @Override
    public OrderPaymentVO convertToPaymentVO(JSONObject jsonObject) {
        OrderPaymentVO vo = new OrderPaymentVO();
        vo.setNonceStr(jsonObject.getString("nonceStr"));
        vo.setPaySign(jsonObject.getString("paySign"));
        vo.setTimeStamp(jsonObject.getString("timeStamp"));
        vo.setSignType(jsonObject.getString("signType"));
        vo.setPackageStr(jsonObject.getString("package"));
        return vo;
    }
}
