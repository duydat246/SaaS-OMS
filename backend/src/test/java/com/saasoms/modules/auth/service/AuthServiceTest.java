package com.saasoms.modules.auth.service;

import com.saasoms.common.config.JwtService;
import com.saasoms.common.exception.BusinessException;
import com.saasoms.modules.auth.dto.AuthResponse;
import com.saasoms.modules.auth.dto.LoginRequest;
import com.saasoms.modules.auth.dto.RefreshRequest;
import com.saasoms.modules.auth.dto.RegisterRequest;
import com.saasoms.modules.auth.entity.RefreshToken;
import com.saasoms.modules.auth.repository.RefreshTokenRepository;
import com.saasoms.modules.shop.entity.Shop;
import com.saasoms.modules.shop.repository.ShopRepository;
import com.saasoms.modules.user.entity.Role;
import com.saasoms.modules.user.entity.User;
import com.saasoms.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String IP = "5.5.5.5";
    private static final String EMAIL = "a@example.com";
    private static final String WRONG_LOGIN_MESSAGE = "Email hoặc mật khẩu không đúng";

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    @InjectMocks
    private AuthService authService;

    // ----------------------------------------------------------------- LOGIN

    @Test
    void login_blocked_throws429BeforeLookup() {
        doThrow(new BusinessException("chặn", HttpStatus.TOO_MANY_REQUESTS))
                .when(loginRateLimiter).checkLogin(IP, EMAIL);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("dung-mat-khau"), IP));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verifyNoInteractions(userRepository, passwordEncoder, refreshTokenRepository);
    }

    @Test
    void login_wrongPassword_recordsFailure() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user("ACTIVE")));
        when(passwordEncoder.matches("sai", "hash")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("sai"), IP));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getMessage()).isEqualTo(WRONG_LOGIN_MESSAGE);
        verify(loginRateLimiter).recordLoginFailure(IP, EMAIL);
        verify(loginRateLimiter, never()).recordLoginSuccess(any(), any());
    }

    @Test
    void login_unknownEmail_recordsFailureWithSameMessage() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("sai"), IP));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getMessage()).isEqualTo(WRONG_LOGIN_MESSAGE);
        verify(loginRateLimiter).recordLoginFailure(IP, EMAIL);
    }

    @Test
    void login_success_resetsCounter() {
        stubSuccessfulLogin("dung-mat-khau");

        AuthResponse response = authService.login(loginRequest("dung-mat-khau"), IP);

        assertThat(response.getUser().getEmail()).isEqualTo(EMAIL);
        InOrder order = inOrder(loginRateLimiter, userRepository);
        order.verify(loginRateLimiter).checkLogin(IP, EMAIL);
        order.verify(userRepository).findByEmail(EMAIL);
        order.verify(loginRateLimiter).recordLoginSuccess(IP, EMAIL);
        verify(loginRateLimiter, never()).recordLoginFailure(any(), any());
    }

    @Test
    void login_oldShortPassword_stillSucceeds() {
        stubSuccessfulLogin("abc123");

        AuthResponse response = authService.login(loginRequest("abc123"), IP);

        assertThat(response.getUser().getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void login_disabledAccount_throws403Vietnamese() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user("DISABLED")));
        when(passwordEncoder.matches("dung-mat-khau", "hash")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("dung-mat-khau"), IP));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getMessage()).isEqualTo("Tài khoản đã bị vô hiệu hoá");
        verify(loginRateLimiter, never()).recordLoginFailure(any(), any());
    }

    // -------------------------------------------------------------- REGISTER

    @Test
    void register_blocked_throws429BeforeLookup() {
        doThrow(new BusinessException("chặn", HttpStatus.TOO_MANY_REQUESTS))
                .when(loginRateLimiter).checkRegister(IP);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(registerRequest(), IP));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verifyNoInteractions(userRepository, shopRepository, passwordEncoder);
    }

    @Test
    void register_duplicateEmail_throws409Vietnamese() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(registerRequest(), IP));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo("Email đã được sử dụng");
        verify(loginRateLimiter).checkRegister(IP);
    }

    @Test
    void register_vietnameseName_generatesAsciiSlug() {
        stubSuccessfulRegister();

        authService.register(registerRequest("Cửa Hàng Đẹp"), IP);

        assertThat(savedShop().getSlug()).isEqualTo("cua-hang-dep");
    }

    @Test
    void register_newShop_storefrontDisabledByDefault() {
        stubSuccessfulRegister();

        authService.register(registerRequest("Cửa Hàng Đẹp"), IP);

        assertThat(savedShop().getStorefrontEnabled()).isFalse();
        assertThat(savedShop().getContactPhone()).isNull();
    }

    @Test
    void register_slugCollision_appendsSuffixWithin100Chars() {
        stubSuccessfulRegister();
        String base = "a".repeat(90);
        when(shopRepository.existsBySlug(base)).thenReturn(true);
        when(shopRepository.existsBySlug(base + "-1")).thenReturn(true);

        authService.register(registerRequest("a".repeat(100)), IP);

        assertThat(savedShop().getSlug()).isEqualTo(base + "-2");
        assertThat(savedShop().getSlug().length()).isLessThanOrEqualTo(100);
    }

    // --------------------------------------------------------------- REFRESH

    @Test
    void refresh_unknownToken_throws400Vietnamese() {
        when(refreshTokenRepository.findByToken("khong-co")).thenReturn(Optional.empty());
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("khong-co");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refresh(request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại");
    }

    // --------------------------------------------------------------- FIXTURE

    private void stubSuccessfulLogin(String password) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user("ACTIVE")));
        when(passwordEncoder.matches(password, "hash")).thenReturn(true);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static LoginRequest loginRequest(String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword(password);
        return request;
    }

    private void stubSuccessfulRegister() {
        when(shopRepository.save(any(Shop.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Shop savedShop() {
        ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
        verify(shopRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private static RegisterRequest registerRequest() {
        return registerRequest("Shop test");
    }

    private static RegisterRequest registerRequest(String shopName) {
        RegisterRequest request = new RegisterRequest();
        request.setShopName(shopName);
        request.setEmail(EMAIL);
        request.setPassword("mat-khau-du-dai");
        request.setFullName("Người dùng");
        return request;
    }

    private static User user(String status) {
        Shop shop = Shop.builder().id(1L).name("Shop test").slug("shop-test").platform("MANUAL").build();
        return User.builder()
                .id(7L)
                .shop(shop)
                .email(EMAIL)
                .password("hash")
                .fullName("Người dùng")
                .role(Role.OWNER)
                .status(status)
                .build();
    }
}
