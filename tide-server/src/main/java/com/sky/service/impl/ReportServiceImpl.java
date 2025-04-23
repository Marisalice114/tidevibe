package com.sky.service.impl;


import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.sql.Date;
import java.util.stream.Collectors;


@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 营业额统计
     * @return
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end){
        //知道字符串两头的日期begin，end，现在需要计算出每日的日期然后将其填入一个list中，数据类型是localdate
        List<LocalDate> dateList = getDateList(begin, end);
        //将这个list转换为字符串，元素之间以逗号分隔
        String dateListStr = StringUtils.join(dateList, ",");
        // 创建查询参数
        Map<String, Object> params = createDateRangeParams(begin, end, Orders.COMPLETED);

        // 查询营业额数据
        List<Map<String, Object>> turnoverData = orderMapper.getSumByDateRange(params);

        // 转换为日期Map
        Map<LocalDate, Double> turnoverDataMap = convertToDateMap(turnoverData, "order_date", "total_amount", Double.class);

        // 生成结果列表，确保每个日期都有数据
        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            Double turnover = turnoverDataMap.getOrDefault(date, 0.0);
            turnoverList.add(turnover);
        }

        // 封装返回结果
        return TurnoverReportVO.builder()
                .dateList(dateListStr)
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
    }

    /**
     * 用户统计
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //先定义出vo要的三张表
        List<LocalDate> dateList = getDateList(begin, end);
        //select count(*) from user where create_time > ? and create_time < ?
        List<Integer> newUserList = new ArrayList<>();
        //select count(*) from user where create_time < ?
        List<Integer> totalUserList = new ArrayList<>();
        //将这个list转换为字符串，元素之间以逗号分隔
        String dateListStr = StringUtils.join(dateList, ",");
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        //一次性查询每天的新增用户数
        List<Map<String,Object>> newUserData = userMapper.getNewUserStatistics(beginTime, endTime);
        //将结果存入Map中便于快速查找
        Map<LocalDate, Integer> newUserDataMap = new HashMap<>();
        for(Map<String,Object> map : newUserData ){
            LocalDate date = ((Date) map.get("user_date")).toLocalDate();
            // 使用Number作为中间类型，避免直接转换Long到Integer导致的ClassCastException
            Number countNumber = (Number) map.get("user_count");
            Integer count = countNumber != null ? countNumber.intValue() : 0;
            newUserDataMap.put(date, count);
        }

        // 查询每天截止时的总用户数
        // 先获取begin日期之前的用户总数作为基础值
        // 保证当前日期没有数据时，totalUserList中的数据不会被覆盖
        Integer baseUserCount = userMapper.getUserCountBefore(beginTime);
        baseUserCount = baseUserCount != null ? baseUserCount : 0;

        // 遍历日期列表，计算每个日期的总用户数
        int runningTotal = baseUserCount;
        for(LocalDate date: dateList){
            int newUsers = newUserDataMap.getOrDefault(date, 0);
            newUserList.add(newUsers);

            //计算总数
            runningTotal += newUsers;
            totalUserList.add(runningTotal);
        }

        return UserReportVO.builder()
                .dateList(dateListStr)
                .newUserList(StringUtils.join(newUserList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .build();
    }

    /**
     * 订单统计
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        //确定需要的内容
        //日期列表，订单数列表，有效订单数列表 订单完成率 订单总数 有效订单数
        //先来处理三张表
        List<LocalDate> dateList = getDateList(begin, end);
        //每日订单数列表
        List<Integer> orderCountList = new ArrayList<>();
        //每日有效订单数李彪
        List<Integer> validOrderCountList = new ArrayList<>();
        //有效订单数就是状态为已完成的订单
        //对这两个表都只进行一次数据查询
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<Map<String, Object>> orderCountData = orderMapper.getCountByDateRange(beginTime, endTime,null);
        List<Map<String, Object>> validOrderCountData = orderMapper.getCountByDateRange(beginTime, endTime, Orders.COMPLETED);

        //将结果转化为map，方便查询
        Map<LocalDate, Integer> orderCountDataMap = new HashMap<>();
        for (Map<String,Object> map :  orderCountData){
            LocalDate date = ((Date) map.get("order_date")).toLocalDate();
            Number countNumber = (Number) map.get("order_count");
            Integer count = countNumber != null ? countNumber.intValue() : 0;
            orderCountDataMap.put(date, count);
        }

        Map<LocalDate, Integer> validOrderCountDataMap = new HashMap<>();
        for (Map<String,Object> map : validOrderCountData){
            LocalDate date = ((Date) map.get("order_date")).toLocalDate();
            Number countNumber = (Number) map.get("order_count");
            Integer count = countNumber != null ? countNumber.intValue() : 0;
            validOrderCountDataMap.put(date, count);
        }

        int totalOrderCount = 0;
        int totalValidOrderCount = 0;
        for (LocalDate date : dateList) {
            Integer orderCount = orderCountDataMap.getOrDefault(date, 0);
            Integer validOrderCount = validOrderCountDataMap.getOrDefault(date, 0);
            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
            totalOrderCount += orderCount;
            totalValidOrderCount += validOrderCount;
        }

        // 计算订单完成率
        Double orderCompletionRate = 0.0;
        if (totalOrderCount != 0) {
            orderCompletionRate = (double) totalValidOrderCount / totalOrderCount;
            // 保留2位小数
            orderCompletionRate = Math.round(orderCompletionRate * 100) / 100.0;
        }
        // 构建并返回结果
        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(totalValidOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    @Override
    public SalesTop10ReportVO getTop10(LocalDate begin, LocalDate end) {
        //返回两个list，一个是商品名称列表，一个是销量列表，然后将其转换为string
        //如何获取top10
        //首先在order表中查询指定时间内的订单，在order_detail中查询所有的商品并排序，返回前十的商品和销量
        //同一种商品会有多种口味，所以只需要通过商品名字判断
        //必须还统计订单是否完成

        // 创建查询参数
        Map<String, Object> params = createDateRangeParams(begin, end, Orders.COMPLETED);
        // 查询销量前10的商品
        List<Map<String, Object>> top10Data = orderMapper.getTop10SalesByDateRange(params);
//        for (Map<String, Object> map : top10Data) {
//            names.add((String) map.get("name"));
//            numbers.add(((Number) map.get("number")).intValue());
//        }
        //stream流
        List<String> names = top10Data.stream()
                .map(map -> (String) map.get("name"))
                .collect(Collectors.toList());
        List<Integer> numbers = top10Data.stream()
                .map(map -> ((Number) map.get("number")).intValue())
                .collect(Collectors.toList());

        // 构建返回结果
        return SalesTop10ReportVO.builder()
                .nameList(StringUtils.join(names, ","))
                .numberList(StringUtils.join(numbers, ","))
                .build();
    }

    /**
     * 导出数据
     * @return
     */
    @Override
    public void export(HttpServletResponse response) {
        //1 查数据库，获取近30天营业数据
        LocalDate dateBegin = LocalDate.now().minusDays(30);
        LocalDate dateEnd = LocalDate.now().minusDays(1);
        BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(dateBegin, LocalTime.MIN),LocalDateTime.of(dateEnd, LocalTime.MAX));
        //2 通过poi将数据写入excel中
        //这里通过类指定反射创建对象，可以保证路径的独立性
        //类加载器已经将src/main/resources作为查找的起点
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/template.xlsx");
        // 设置响应内容类型
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        // 设置Content-Disposition头，指定文件名
        response.setHeader("Content-Disposition", "attachment;filename=business_report.xlsx");
        try {
            XSSFWorkbook excel = new XSSFWorkbook(in);

            //填充数据 -- 时间
            //获取表格标签页
            XSSFSheet sheet1 = excel.getSheetAt(0);
            sheet1.getRow(1).getCell(1).setCellValue("时间:"+dateBegin+ "至" + dateEnd);
            XSSFRow row = sheet1.getRow(3);
            row.getCell(1).setCellValue("营业额");
            row.getCell(2).setCellValue(businessData.getTurnover());
            row.getCell(3).setCellValue("订单完成率");
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
            row.getCell(5).setCellValue("新增用户数");
            row.getCell(6).setCellValue(businessData.getNewUsers());
            row = sheet1.getRow(4);
            row.getCell(1).setCellValue("有效订单");
            row.getCell(2).setCellValue(businessData.getValidOrderCount());
            row.getCell(3).setCellValue("订单单价");
            row.getCell(4).setCellValue(businessData.getUnitPrice());
            row = sheet1.getRow(6);
            row.getCell(1).setCellValue("日期");
            row.getCell(2).setCellValue("营业额");
            row.getCell(3).setCellValue("有效订单");
            row.getCell(4).setCellValue("订单完成率");
            row.getCell(5).setCellValue("平均客单价");
            row.getCell(6).setCellValue("新增用户数");

            //每日的详细数据
            for(int i = 0; i < 30; i++){
                LocalDate date = dateBegin.plusDays(i);
                //查出当天数据
                BusinessDataVO currentBusinessData = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));
                row = sheet1.getRow(i + 7);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(currentBusinessData.getTurnover());
                row.getCell(3).setCellValue(currentBusinessData.getValidOrderCount());
                row.getCell(4).setCellValue(currentBusinessData.getOrderCompletionRate());
                row.getCell(5).setCellValue(currentBusinessData.getUnitPrice());
                row.getCell(6).setCellValue(currentBusinessData.getNewUsers());
            }


            //3 通过输出流将excel文件下载到客户端浏览器
            ServletOutputStream outputStream = response.getOutputStream();
            excel.write(outputStream);

            //关闭资源
            excel.close();
            outputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private List<LocalDate> getDateList(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        //注意这里经过循环过后，begin的值变了，之后还要用到begin，所以需要将其转换成别的变量送入while
        LocalDate currentDate = begin;
        dateList.add(currentDate);
        while (!currentDate.equals(end)) {
            currentDate = currentDate.plusDays(1);
            dateList.add(currentDate);
        }
        return dateList;
    }

    /**
     * 创建日期范围查询参数
     * @param begin 开始日期
     * @param end 结束日期
     * @param status 订单状态，可为null
     * @return 查询参数Map
     */
    private Map<String, Object> createDateRangeParams(LocalDate begin, LocalDate end, Integer status) {
        Map<String, Object> map = new HashMap<>();
        map.put("begin", LocalDateTime.of(begin, LocalTime.MIN));
        map.put("end", LocalDateTime.of(end, LocalTime.MAX));
        if (status != null) {
            map.put("status", status);
        }
        return map;
    }

    /**
     * 将查询结果转换为日期为键的Map
     * @param data 查询结果列表
     * @param dateKey 日期字段名
     * @param valueKey 值字段名
     * @param valueType 值类型
     * @return 转换后的Map
     */
    private <T> Map<LocalDate, T> convertToDateMap(List<Map<String, Object>> data,
                                                   String dateKey, String valueKey,
                                                   Class<T> valueType) {
        Map<LocalDate, T> resultMap = new HashMap<>();
        for (Map<String, Object> map : data) {
            LocalDate date = ((Date) map.get(dateKey)).toLocalDate();
            Object rawValue = map.get(valueKey);

            // 适当的类型转换
            if (rawValue instanceof BigDecimal && valueType == Double.class) {
                resultMap.put(date, valueType.cast(((BigDecimal) rawValue).doubleValue()));
            } else if (rawValue instanceof Number && valueType == Integer.class) {
                resultMap.put(date, valueType.cast(((Number) rawValue).intValue()));
            } else {
                resultMap.put(date, valueType.cast(rawValue));
            }
        }
        return resultMap;
    }
}
