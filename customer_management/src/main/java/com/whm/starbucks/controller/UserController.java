package com.whm.starbucks.controller;

import com.whm.starbucks.pojo.dto.UserDto;
import com.whm.starbucks.pojo.vo.ResponseVo;
import com.whm.starbucks.pojo.entity.User;
import com.whm.starbucks.pojo.vo.UserVo;
import com.whm.starbucks.services.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserService userService;

    @PostMapping("/register")
    public ResponseVo register(@RequestBody UserDto user) {
        // 异常交给 GlobalExceptionHandler 统一处理
        UserVo userInfo = userService.registerUser(user);
        return ResponseVo.success("注册成功", userInfo);
    }

    @GetMapping("/get")
    public ResponseVo getUserById(@RequestParam("id") Long id) {
        User user = userService.getUserById(id);
        return ResponseVo.success(user);
    }

    @PostMapping("/delete")
    public ResponseVo deleteUserById(@RequestBody UserDto user) {
        int result = userService.deleteUserById(user.getId());
        return ResponseVo.success(result);
    }

}
