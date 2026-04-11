package com.saasoms.modules.customer.repository;

import com.saasoms.modules.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByShopIdAndPhone(Long shopId, String phone);
    Optional<Customer> findByIdAndShopId(Long id, Long shopId);
}
