package com.saasoms.modules.product.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductImageResponse {

    private Long id;
    private String url;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private Integer sortOrder;
    private boolean primary;
}
