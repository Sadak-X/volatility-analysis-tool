package com.volatility.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.volatility.common.exception.BusinessException;
import com.volatility.common.security.JwtTokenProvider;
import com.volatility.modules.auth.dto.LoginResponse;
import com.volatility.modules.auth.dto.UserProfile;
import com.volatility.modules.auth.entity.UserEntity;
import com.volatility.modules.auth.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, jwtTokenProvider);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerReturnsTokenWhenUsernameIsValid() {
        // TC-AUTH-01
        String username = "testUser";
        String password = "password123";
        String nickname = "Test User";

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(jwtTokenProvider.generateToken(1L, username)).thenReturn("mock-token");

        LoginResponse response = authService.register(username, password, nickname);

        assertThat(response.token()).isEqualTo("mock-token");
        assertThat(response.user().username()).isEqualTo(username);
        assertThat(response.user().nickname()).isEqualTo(nickname);
        assertThat(response.user().roleCode()).isEqualTo("USER");
    }

    @Test
    void registerThrowsWhenUsernameTooShort() {
        // TC-AUTH-02
        String username = "ab";
        assertThatThrownBy(() -> authService.register(username, "password123", "nick"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 1004);
    }

    @Test
    void registerSucceedsWhenUsernameLengthIsThree() {
        // TC-AUTH-03
        String username = "abc";
        String password = "password123";

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(jwtTokenProvider.generateToken(1L, username)).thenReturn("mock-token");

        LoginResponse response = authService.register(username, password, null);

        assertThat(response.token()).isEqualTo("mock-token");
        assertThat(response.user().username()).isEqualTo(username);
        assertThat(response.user().nickname()).isEqualTo(username); // 默认昵称为用户名
    }

    @Test
    void registerSucceedsWhenUsernameLengthIsSixtyFour() {
        // TC-AUTH-04
        String username = "a".repeat(64);
        String password = "password123";

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(jwtTokenProvider.generateToken(1L, username)).thenReturn("mock-token");

        LoginResponse response = authService.register(username, password, null);

        assertThat(response.token()).isEqualTo("mock-token");
        assertThat(response.user().username()).isEqualTo(username);
    }

    @Test
    void registerThrowsWhenUsernameTooLong() {
        // TC-AUTH-05
        String username = "a".repeat(65);
        assertThatThrownBy(() -> authService.register(username, "password123", "nick"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 1004);
    }

    @Test
    void registerThrowsWhenUsernameAlreadyExists() {
        // TC-AUTH-06
        String username = "testUser";
        when(userRepository.existsByUsername(username)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(username, "password123", "nick"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 1003);
    }

    @Test
    void loginReturnsTokenWhenCredentialsAreValid() {
        // TC-AUTH-07
        String username = "testUser";
        String rawPassword = "password123";
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setNickname("Test User");
        user.setStatus(1);
        user.setRoleCode("USER");

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(1L, username)).thenReturn("mock-token");

        LoginResponse response = authService.login(username, rawPassword);

        assertThat(response.token()).isEqualTo("mock-token");
        assertThat(response.user().username()).isEqualTo(username);
    }

    @Test
    void loginThrowsWhenUsernameNotFound() {
        // TC-AUTH-08
        String username = "unknown";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(username, "password"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 1001);
    }

    @Test
    void loginThrowsWhenPasswordIsIncorrect() {
        // TC-AUTH-09
        String username = "testUser";
        String rawPassword = "password123";
        String wrongPassword = "wrong";
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setNickname("Test User");
        user.setStatus(1);
        user.setRoleCode("USER");

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(username, wrongPassword))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 1001);
    }

    @Test
    void currentUserReturnsProfileWhenAuthenticated() {
        // TC-AUTH-10
        String username = "testUser";
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername(username);
        user.setNickname("Test User");
        user.setRoleCode("USER");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null)
        );
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        UserProfile profile = authService.currentUser();

        assertThat(profile.id()).isEqualTo(1L);
        assertThat(profile.username()).isEqualTo(username);
        assertThat(profile.nickname()).isEqualTo("Test User");
        assertThat(profile.roleCode()).isEqualTo("USER");
    }
}