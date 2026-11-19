package com.whm.starbucks.services;

import com.whm.starbucks.dao.UserRepository;
import com.whm.starbucks.pojo.dto.UserDto;
import com.whm.starbucks.pojo.entity.User;
import com.whm.starbucks.pojo.vo.UserVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

@Service
public class UserService {

    @Resource
    private UserRepository userRepository;

    /**
     * 越权访问：/user/get?id=N 任何登录用户都能查任意用户。目前 authentication.getPrincipal() 里存的就是 userId，可以加一行判断「只能查自己，或必须是管理员」。
     */
    public User getUserById(Long id) {
       return userRepository.getUserById(id);
    }

    public User getUserByPhone(String phone) {return userRepository.getUserByPhone(phone);}

    @Transactional(rollbackFor = Exception.class)
    public UserVo registerUser(UserDto userDto) {
        if (StringUtils.isEmpty(userDto.getNickname())) {
            throw new IllegalArgumentException("Nickname cannot be empty");
        }
        if (StringUtils.isEmpty(userDto.getPassword())) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if (StringUtils.isEmpty(userDto.getPhone())) {
            throw new IllegalArgumentException("Phone cannot be empty");
        }

        User user = new User();
        user.setNickname(userDto.getNickname());
        user.setPassword(userDto.getPassword());
        user.setPhone(userDto.getPhone());

        int count = userRepository.registerUser(user);
        if (count == 0) {
            throw new RuntimeException("Failed to register user");
        }
        User userInfo = userRepository.getUserByPhone(user.getPhone());

        if (userInfo == null) {
            throw new RuntimeException("Failed to register user");
        }

        UserVo userVo = new UserVo();
        userVo.setNickname(userInfo.getNickname());
        userVo.setPhone(userInfo.getPhone());
        userVo.setMemberLevel(userInfo.getMemberLevel());
        userVo.setStarBalance(userInfo.getStarBalance());
        userVo.setCardBalance(userInfo.getCardBalance());
        return userVo;
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteUserById(Long id) {
        return userRepository.deleteUserById(id);
    }
}
