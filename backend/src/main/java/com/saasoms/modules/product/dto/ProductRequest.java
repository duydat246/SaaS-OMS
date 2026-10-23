package com.saasoms.modules.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 200, message = "Tên sản phẩm tối đa 200 ký tự")
    private String name;

    private String description;

    @Size(max = 100, message = "Danh mục tối đa 100 ký tự")
    private String category;

    @Size(max = 20, message = "Nền tảng nguồn tối đa 20 ký tự")
    private String platformSource;

    @Size(max = 500, message = "URL ảnh tối đa 500 ký tự")
    private String imageUrl;

    @Size(max = 10, message = "Tối đa 10 ảnh mỗi sản phẩm")
    private List<Long> imageIds;

    private Long primaryImageId;

    @NotEmpty(message = "Sản phẩm phải có ít nhất 1 SKU")
    @Valid
    private List<SkuRequest> skus;
}
