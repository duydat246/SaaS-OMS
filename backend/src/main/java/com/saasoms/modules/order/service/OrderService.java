package com.saasoms.modules.order.service;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.response.PageResponse;
import com.saasoms.common.tenant.TenantContext;
import com.saasoms.modules.customer.entity.Customer;
import com.saasoms.modules.customer.repository.CustomerRepository;
import com.saasoms.modules.order.dto.CreateOrderRequest;
import com.saasoms.modules.order.dto.OrderResponse;
import com.saasoms.modules.order.dto.OrderStatsResponse;
import com.saasoms.modules.order.dto.UpdateStatusRequest;
import com.saasoms.modules.order.entity.Order;
import com.saasoms.modules.order.entity.OrderItem;
import com.saasoms.modules.order.entity.OrderStatus;
import com.saasoms.modules.order.repository.OrderRepository;
import com.saasoms.modules.shop.entity.Shop;
import com.saasoms.modules.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            OrderStatus.PENDING,   EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.SHIPPED,   OrderStatus.CANCELLED),
            OrderStatus.SHIPPED,   EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
            OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class)
    );

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ShopRepository shopRepository;

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrders(OrderStatus status, String search, Pageable pageable) {
        Long tenantId = TenantContext.get();
        Page<Order> page = orderRepository.search(tenantId, status, search, pageable);
        return PageResponse.of(page.map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public OrderStatsResponse getOrderStats() {
        Long tenantId = TenantContext.get();
        long total = orderRepository.countByShop_Id(tenantId);
        long pending = orderRepository.countByShop_IdAndStatus(tenantId, OrderStatus.PENDING);
        long delivered = orderRepository.countByShop_IdAndStatus(tenantId, OrderStatus.DELIVERED);
        BigDecimal revenue = orderRepository.sumTotalAmountByShopIdAndStatus(tenantId, OrderStatus.DELIVERED);
        return new OrderStatsResponse(total, pending, delivered, revenue);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        Long tenantId = TenantContext.get();
        Order order = orderRepository.findByIdAndShopId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Order not found"));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req) {
        Long tenantId = TenantContext.get();
        Shop shop = shopRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Shop not found"));

        // Upsert customer by phone
        Customer customer = customerRepository
                .findByShopIdAndPhone(tenantId, req.getCustomerPhone())
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .shop(shop)
                        .fullName(req.getCustomerName())
                        .phone(req.getCustomerPhone())
                        .address(req.getCustomerAddress())
                        .build()));

        // Build order items and compute subtotal
        List<OrderItem> items = req.getItems().stream().map(i -> {
            BigDecimal total = i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()));
            return OrderItem.builder()
                    .productName(i.getProductName())
                    .variant(i.getVariant())
                    .quantity(i.getQuantity())
                    .unitPrice(i.getUnitPrice())
                    .totalPrice(total)
                    .build();
        }).toList();

        BigDecimal subtotal = items.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = subtotal
                .add(req.getShippingFee())
                .subtract(req.getDiscount());

        Order order = Order.builder()
                .shop(shop)
                .customer(customer)
                .shippingAddress(req.getCustomerAddress())
                .shippingFee(req.getShippingFee())
                .discount(req.getDiscount())
                .subtotal(subtotal)
                .totalAmount(total)
                .notes(req.getNotes())
                .build();

        items.forEach(item -> {
            item.setOrder(order);
            order.getItems().add(item);
        });

        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse updateStatus(Long id, UpdateStatusRequest req) {
        Long tenantId = TenantContext.get();
        Order order = orderRepository.findByIdAndShopId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Order not found"));

        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(order.getStatus(), EnumSet.noneOf(OrderStatus.class));
        if (!allowed.contains(req.getStatus())) {
            throw BusinessException.badRequest(
                    "Cannot transition from " + order.getStatus() + " to " + req.getStatus());
        }

        order.setStatus(req.getStatus());
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional
    public void deleteOrder(Long id) {
        Long tenantId = TenantContext.get();
        Order order = orderRepository.findByIdAndShopId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Order not found"));

        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw BusinessException.badRequest("Cannot delete a delivered order");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }
}
