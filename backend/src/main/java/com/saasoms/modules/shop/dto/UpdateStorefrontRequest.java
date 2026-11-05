package com.saasoms.modules.shop.dto;

import com.saasoms.common.util.SlugPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateStorefrontRequest {

    @NotNull(message = "Trạng thái bật/tắt trang bán hàng không được để trống")
    private Boolean storefrontEnabled;

    @NotBlank(message = "Địa chỉ cửa hàng không được để trống")
    @Pattern(regexp = SlugPolicy.REGEX,
            message = "Địa chỉ cửa hàng dài 3-100 ký tự, chỉ gồm chữ thường không dấu, số và dấu gạch ngang")
    private String slug;

    /** {@code null} hoặc chuỗi rỗng nghĩa là xoá số liên hệ. */
    @Pattern(regexp = "^$|^\\+?[0-9]{8,15}$",
            message = "Số điện thoại liên hệ gồm 8-15 chữ số, có thể bắt đầu bằng dấu +")
    private String contactPhone;
}
