package com.saasoms.common.response;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public class PageResponse<T> {

    private final List<T> content;
    private final Meta meta;

    private PageResponse(List<T> content, Meta meta) {
        this.content = content;
        this.meta = meta;
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        Meta meta = new Meta(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
        return new PageResponse<>(page.getContent(), meta);
    }

    @Getter
    public static class Meta {
        private final int page;
        private final int size;
        private final long totalElements;
        private final int totalPages;

        public Meta(int page, int size, long totalElements, int totalPages) {
            this.page = page;
            this.size = size;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
        }
    }
}
