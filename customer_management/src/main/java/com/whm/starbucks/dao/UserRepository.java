package com.whm.starbucks.dao;


import com.whm.starbucks.pojo.entity.User;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository {
    User getUserById(@Param("id") Long id);

    int registerUser(@Param("user") User user);

    User getUserByPhone(@Param("phone") String phone);

    int deleteUserById(@Param("id") Long id);
}
