package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.sky.service.PaymentService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 真实微信支付服务实现
 */
@Service
@Profile("prod")
public class WeChatPaymentServiceImpl implements PaymentService {

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Override
    public JSONObject createPayment(String orderNum, BigDecimal amount, String description, String openid) throws Exception {
        // 调用微信支付接口
        return weChatPayUtil.pay(orderNum, amount, description, openid);
    }

    @Override
    public boolean handlePaymentResult(String orderNum) {
        // 这里应该处理微信支付回调通知
        // 在实际项目中，微信会发送回调通知确认支付结果
        // 这里简化处理，直接返回true
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