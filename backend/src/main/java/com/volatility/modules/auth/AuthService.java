package com.volatility.modules.auth;

import com.volatility.common.exception.BusinessException;
import com.volatility.common.security.JwtTokenProvider;
import com.volatility.modules.auth.dto.LoginResponse;
import com.volatility.modules.auth.dto.UserProfile;
import com.volatility.modules.auth.entity.UserEntity;
import com.volatility.modules.auth.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse login(String username, String password) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(1001, "用户名或密码错误"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(1001, "用户名或密码错误");
        }
        return new LoginResponse(
                jwtTokenProvider.generateToken(user.getId(), user.getUsername()),
                toProfile(user)
        );
    }

    public LoginResponse register(String username, String password, String nickname) {
        String normalizedUsername = username.trim();
        if (normalizedUsername.length() < 3 || normalizedUsername.length() > 64) {
            throw new BusinessException(1004, "用户名长度需为 3-64 个字符");
        }
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new BusinessException(1003, "用户名已存在");
        }

        UserEntity user = new UserEntity();
        user.setUsername(normalizedUsername);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(normalizeNickname(nickname, normalizedUsername));
        user.setStatus(1);
        user.setRoleCode("USER");
        UserEntity savedUser = userRepository.save(user);

        return new LoginResponse(
                jwtTokenProvider.generateToken(savedUser.getId(), savedUser.getUsername()),
                toProfile(savedUser)
        );
    }

    public UserProfile currentUser() {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(1002, "用户不存在"));
        return toProfile(user);
    }

    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    private String normalizeNickname(String nickname, String username) {
        if (nickname == null || nickname.isBlank()) {
            return username;
        }
        return nickname.trim();
    }

    private UserProfile toProfile(UserEntity user) {
        return new UserProfile(user.getId(), user.getUsername(), user.getNickname(), user.getRoleCode());
    }
}
