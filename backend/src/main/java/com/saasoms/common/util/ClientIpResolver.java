package com.saasoms.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Lấy IP client để giới hạn số lần thử. nginx ghi đè X-Real-IP bằng IP đã xác thực, nên không đọc
 * X-Forwarded-For. IPv6 gom về /64 vì một thuê bao thường được cấp nguyên dải này.
 */
public final class ClientIpResolver {

    private static final String REAL_IP_HEADER = "X-Real-IP";
    private static final int MAX_IP_LITERAL_LENGTH = 45;

    private static final Pattern IPV4 = Pattern.compile(
            "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}");

    /**
     * Header do client có thể tác động nên không được phân giải DNS. {@link InetAddress#getByName}
     * chỉ không tra DNS khi chuỗi bắt đầu bằng hex hoặc dấu hai chấm và có chứa dấu hai chấm;
     * mẫu này bảo đảm cả hai điều kiện trước khi gọi.
     */
    private static final Pattern IPV6_CANDIDATE = Pattern.compile("[0-9a-fA-F:][0-9a-fA-F.]*:[0-9a-fA-F:.]*");

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String fromHeader = normalize(request.getHeader(REAL_IP_HEADER));
        if (fromHeader != null) {
            return fromHeader;
        }
        String remoteAddr = request.getRemoteAddr();
        String normalized = normalize(remoteAddr);
        return normalized != null ? normalized : remoteAddr;
    }

    /** Trả về khoá đã chuẩn hoá, hoặc null nếu chuỗi không phải IP literal. */
    private static String normalize(String value) {
        if (value == null || value.length() > MAX_IP_LITERAL_LENGTH) {
            return null;
        }
        if (IPV4.matcher(value).matches()) {
            return value;
        }
        if (!IPV6_CANDIDATE.matcher(value).matches()) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return address instanceof Inet6Address ? prefix64(address.getAddress()) : address.getHostAddress();
        } catch (UnknownHostException | IllegalArgumentException ex) {
            return null;
        }
    }

    private static String prefix64(byte[] bytes) {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < 8; i += 2) {
            key.append(Integer.toHexString(((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF))).append(':');
        }
        return key.append(":/64").toString();
    }
}
