package com.whm.starbucks.controller;

import com.whm.starbucks.pojo.dto.LoginDto;
import com.whm.starbucks.pojo.dto.RefreshTokenDto;
import com.whm.starbucks.pojo.vo.LoginVo;
import com.whm.starbucks.pojo.vo.ResponseVo;
import com.whm.starbucks.services.LoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final LoginService loginService;

    @PostMapping("/login")
    public ResponseVo<LoginVo> login(@RequestBody LoginDto req) {


        LoginVo loginVo = loginService.login(req);

        return ResponseVo.success(loginVo);
    }

    @PostMapping("/refresh")
    public ResponseVo<LoginVo> refresh(@RequestBody RefreshTokenDto refreshToken) {
        LoginVo loginVo = loginService.refresh(refreshToken.getRefreshToken());

        return ResponseVo.success(loginVo);
    }

    /**
     * Authorization 头声明为可选：登出本身靠 refresh token 完成，
     * access token 可能已过期或客户端未携带，不能因此让登出失败。
     */
    @PostMapping("/logoutAll")
    public ResponseVo<LoginVo> logoutAll(@RequestBody RefreshTokenDto refreshToken,
                                        @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        loginService.logoutAll(refreshToken.getRefreshToken(), authorizationHeader);
        return ResponseVo.success("Logged out successfully", null);
    }

    @PostMapping("/logout")
    public ResponseVo<LoginVo> logout(@RequestBody RefreshTokenDto refreshToken,
                                     @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        loginService.logout(refreshToken.getRefreshToken(), authorizationHeader);
        return ResponseVo.success("Logged out successfully", null);
    }
}

