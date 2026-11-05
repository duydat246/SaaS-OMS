package com.saasoms.modules.auth.service;

import com.saasoms.common.config.JwtService;
import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.util.SlugPolicy;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String WRONG_LOGIN_MESSAGE = "Email hoặc mật khẩu không đúng";

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;

    @Value("${app.jwt.refresh-token-expiry-days}")
    private int refreshTokenExpiryDays;

    @Transactional
    public AuthResponse register(RegisterRequest req, String clientIp) {
        loginRateLimiter.checkRegister(clientIp);

        if (userRepository.existsByEmail(req.getEmail())) {
            throw BusinessException.conflict("Email đã được sử dụng");
        }

        String slug = generateSlug(req.getShopName());

        Shop shop = shopRepository.save(Shop.builder()
                .name(req.getShopName())
                .slug(slug)
                .platform(req.getPlatform())
                .build());

        User user = userRepository.save(User.builder()
                .shop(shop)
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .role(Role.OWNER)
                .build());

        return buildAuthResponse(user, shop);
    }

    @Transactional
    public AuthResponse login(LoginRequest req, String clientIp) {
        // Chặn trước khi tra DB và trước BCrypt: lượt bị chặn không tốn tài nguyên và không lộ gì.
        loginRateLimiter.checkLogin(clientIp, req.getEmail());

        User user = userRepository.findByEmail(req.getEmail()).orElse(null);

        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            loginRateLimiter.recordLoginFailure(clientIp, req.getEmail());
            throw new BusinessException(WRONG_LOGIN_MESSAGE, HttpStatus.UNAUTHORIZED);
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw BusinessException.forbidden("Tài khoản đã bị vô hiệu hoá");
        }

        loginRateLimiter.recordLoginSuccess(clientIp, req.getEmail());
        return buildAuthResponse(user, user.getShop());
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(req.getRefreshToken())
                .orElseThrow(() -> BusinessException.badRequest("Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại"));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw BusinessException.badRequest("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
        }

        User user = refreshToken.getUser();
        refreshTokenRepository.delete(refreshToken);

        return buildAuthResponse(user, user.getShop());
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(refreshTokenRepository::delete);
    }

    private AuthResponse buildAuthResponse(User user, Shop shop) {
        String accessToken = jwtService.generateAccessToken(
                user.getEmail(), user.getId(), shop.getId(), user.getRole().name());

        RefreshToken refreshToken = refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryDays))
                .build());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(user.getRole().name())
                        .tenantId(shop.getId())
                        .shopName(shop.getName())
                        .build())
                .build();
    }

    private String generateSlug(String shopName) {
        String base = SlugPolicy.fromName(shopName);

        String slug = base;
        int suffix = 1;
        while (shopRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }
}
