package com.saasoms.modules.shop.repository;

import com.saasoms.modules.shop.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShopRepository extends JpaRepository<Shop, Long> {
    boolean existsBySlug(String slug);
    Optional<Shop> findBySlug(String slug);
}
