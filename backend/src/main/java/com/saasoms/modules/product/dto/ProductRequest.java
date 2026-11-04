package com.saasoms.modules.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

    @Size(max = 10000, message = "Mô tả tối đa 10000 ký tự")
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
    private List<@NotNull(message = "Danh sách SKU không được chứa phần tử rỗng") @Valid SkuRequest> skus;

    /**
     * Id các SKU client đã thấy lúc mở form. SKU trong DB nằm ngoài danh sách này thì không được
     * coi là "người dùng đã xoá". Null là client cũ, giữ hành vi cũ. POST bỏ qua trường này.
     */
    @Size(max = 1000, message = "Danh sách biến thể đã biết tối đa 1000 phần tử")
    private List<Long> knownSkuIds;
}
