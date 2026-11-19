package com.whm.starbucks.services;

import com.baomidou.mybatisplus.extension.api.R;
import com.whm.starbucks.config.AccessTokenBlacklist;
import com.whm.starbucks.config.JwtService;
import com.whm.starbucks.config.RefreshTokenStore;
import com.whm.starbucks.exceptions.BizException;
import com.whm.starbucks.pojo.dto.LoginDto;
import com.whm.starbucks.pojo.entity.User;
import com.whm.starbucks.pojo.vo.LoginVo;
import com.whm.starbucks.pojo.vo.ResponseVo;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@Slf4j
public class LoginService {
    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private AccessTokenBlacklist accessTokenBlacklist;

    public LoginVo login(LoginDto req) {
        if (StringUtils.isEmpty(req.getPhone())) {
            throw new BizException(400, "Phone cannot be empty");
        }
        if (StringUtils.isEmpty(req.getPassword())) {
            throw new BizException(400, "Password cannot be empty");
        }


        // 判断这个用户是否存在 ，不存在注册，存在则登录
        User user = userService.getUserByPhone(req.getPhone());

        if (user == null) {
            throw new BizException(401, "Incorrect User or password");
        } else if (!req.getPassword().equals(user.getPassword())) {
            throw new BizException(401, "Incorrect User or password");
        }

       // 生成Token
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getMemberLevel());

        Map.Entry<String, String> refreshEntry = jwtService.generateRefreshToken(user.getId());

        String refreshToken = refreshEntry.getKey();
        String jti = refreshEntry.getValue();
        // 存redis
        // 4. Refresh Token 的 jti 写入 Redis 白名单（7天）
        refreshTokenStore.store(user.getId(), jti, jwtService.getRefreshExpiration());

        LoginVo lv = new LoginVo();
        lv.setPhone(user.getPhone());
        lv.setUserId(user.getId());
        lv.setToken(accessToken);
        lv.setExpireTime(jwtService.getAccessExpiration());
        // Refresh Token 需返回给客户端，供 /auth/refresh 换取新的 Access Token
        lv.setRefreshToken(refreshToken);
        lv.setRefreshExpireTime(jwtService.getRefreshExpiration());

        return lv;
    }

    public LoginVo refresh(String refreshToken) {
        /**
         * 从请求体拿到 refresh token（不能放 Authorization 头，否则会被 JwtAuthenticationFilter 当成 access token 拒掉）
         * parseToken 验签 + 验过期 → 失败抛 401
         * 校验 type == "refresh" → 防止拿 access token 来换新 token
         * 查 Redis 白名单 isValid(userId, jti) → 查不到说明已登出/已被踢/已轮换 → 401
         * 重新查一次用户（拿到最新的 memberLevel，否则续出来的 token 里的等级还是旧的），签发新 access token
         */
        if (StringUtils.isEmpty(refreshToken)) {
            throw new BizException(400, "Refresh Token cannot be empty");
        }

        Claims claims;
        try {
            claims = jwtService.parseToken(refreshToken);

        } catch (Exception e) {

            throw new BizException(401, "Invalid Refresh Token");
        }
        // 防止用 access token 冒充 refresh token
        if (!jwtService.isRefreshToken(claims)) {
            throw new BizException(401, "Invalid Refresh Token");
        }
        Long userId = Long.valueOf(claims.getSubject());
        String jti = claims.getId();
        // 必须是 Redis 白名单里仍然有效的那个 jti
        if (!refreshTokenStore.isValid(userId, jti)) {
            throw new BizException(401, "Invalid Refresh Token");
        }
        User user = userService.getUserById(userId);
        if (user == null) {
            refreshTokenStore.revoke(userId, jti);
            throw new BizException(401, "User not found");
        }
        // 轮换：旧 jti 立即作废，签发新的 refresh token
        refreshTokenStore.revoke(userId, jti);
        Map.Entry<String, String> refreshEntry = jwtService.generateRefreshToken(userId);
        refreshTokenStore.store(userId, refreshEntry.getValue(), jwtService.getRefreshExpiration());

        LoginVo lv = new LoginVo();
        lv.setPhone(user.getPhone());
        lv.setUserId(user.getId());
        lv.setToken(jwtService.generateAccessToken(user.getId(), user.getMemberLevel()));
        lv.setExpireTime(jwtService.getAccessExpiration());
        lv.setRefreshToken(refreshEntry.getKey());
        lv.setRefreshExpireTime(jwtService.getRefreshExpiration());
        return lv;
    }

    public void logout(String refreshToken, String authorizationHeader) {
        revokeRefreshToken(refreshToken, false);
        blacklistAccessToken(authorizationHeader);
    }

    public void logoutAll(String refreshToken, String authorizationHeader) {
        revokeRefreshToken(refreshToken, true);
        blacklistAccessToken(authorizationHeader);
    }

    /**
     * 撤销 refresh token。登出必须幂等：refresh 已过期/已撤销/非法时按“已登出”处理，
     * 不能因此抛错而阻断后面的 access token 拉黑。
     */
    private void revokeRefreshToken(String refreshToken, boolean allDevices) {
        if (StringUtils.isEmpty(refreshToken)) {
            throw new BizException(400, "Refresh token cannot be empty");
        }
        Claims claims;
        try {
            claims = jwtService.parseToken(refreshToken);
        } catch (Exception e) {
            log.debug("登出时 refresh token 已失效，按已登出处理: {}", e.getMessage());
            return;
        }
        if (!jwtService.isRefreshToken(claims)) {
            log.debug("登出传入的不是 refresh token，忽略");
            return;
        }
        Long userId = Long.valueOf(claims.getSubject());
        if (allDevices) {
            refreshTokenStore.revokeAll(userId);
        } else {
            refreshTokenStore.revoke(userId, claims.getId());
        }
    }

    /** 拉黑当前 access token；缺失/过期/非法都只记录日志，不影响登出结果 */
    private void blacklistAccessToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            return;
        }
        try {
            Claims access = jwtService.parseToken(authorizationHeader.substring(7).trim());
            if (jwtService.isAccessToken(access)) {
                accessTokenBlacklist.revoke(access.getId(), jwtService.remainingMillis(access));
            }
        } catch (Exception e) {
            log.debug("登出时 access token 无需拉黑: {}", e.getMessage());
        }
    }

}
