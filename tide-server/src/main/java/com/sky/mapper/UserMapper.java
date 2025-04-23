package com.sky.mapper;


import com.sky.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    /**
     * 新增用户
     * @param user
     */
    void insert(User user);

    /**
     * 根据id来查询用户
     * @param userId
     * @return
     */
    @Select("select * from user where id = #{userId}")
    User getById(Long userId);

    /**
     * 获取每天新增用户数
     * @param beginTime
     * @param endTime
     * @return
     */
    @MapKey("user_date")
    List<Map<String, Object>> getNewUserStatistics(LocalDateTime beginTime, LocalDateTime endTime);

    /**
     * 获取之前用户数
     * @param beginTime
     * @return
     */
    @Select("select count(id) from user where create_time < #{beginTime}")
    Integer getUserCountBefore(LocalDateTime beginTime);
}
