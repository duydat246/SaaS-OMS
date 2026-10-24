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
import com.saasoms.modules.product.entity.ProductStatus;
import com.saasoms.modules.product.entity.Sku;
import com.saasoms.modules.product.repository.SkuRepository;
import com.saasoms.modules.shop.entity.Shop;
import com.saasoms.modules.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /** Trạng thái đã trừ tồn kho, huỷ từ các trạng thái này phải hoàn tồn. */
    private static final Set<OrderStatus> STOCK_DEDUCTED_STATUSES =
            EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED);

    private static final Map<OrderStatus, String> STATUS_LABELS = Map.of(
            OrderStatus.PENDING, "Chờ xác nhận",
            OrderStatus.CONFIRMED, "Đã xác nhận",
            OrderStatus.SHIPPED, "Đang giao",
            OrderStatus.DELIVERED, "Đã giao",
            OrderStatus.CANCELLED, "Đã huỷ"
    );

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ShopRepository shopRepository;
    private final SkuRepository skuRepository;

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrders(OrderStatus status, String search, Pageable pageable) {
        Long tenantId = requireTenant();
        Page<Order> page = orderRepository.search(tenantId, status, search, pageable);
        return PageResponse.of(page.map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public OrderStatsResponse getOrderStats() {
        Long tenantId = requireTenant();
        long total = orderRepository.countByShop_Id(tenantId);
        long pending = orderRepository.countByShop_IdAndStatus(tenantId, OrderStatus.PENDING);
        long delivered = orderRepository.countByShop_IdAndStatus(tenantId, OrderStatus.DELIVERED);
        BigDecimal revenue = orderRepository.sumTotalAmountByShopIdAndStatus(tenantId, OrderStatus.DELIVERED);
        return new OrderStatsResponse(total, pending, delivered, revenue);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        Long tenantId = requireTenant();
        return OrderResponse.from(findOrder(tenantId, id));
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req) {
        Long tenantId = requireTenant();
        Shop shop = shopRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy cửa hàng"));

        assertNoDuplicateSkus(req.getItems());
        Map<Long, Sku> skusById = loadSkus(tenantId, req.getItems().stream()
                .map(CreateOrderRequest.OrderItemRequest::getSkuId)
                .toList());

        List<OrderItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CreateOrderRequest.OrderItemRequest line : req.getItems()) {
            if (line.getSkuId() == null) {
                throw BusinessException.badRequest(
                        "Vui lòng chọn sản phẩm cho tất cả các dòng");
            }
            if (line.getQuantity() == null || line.getQuantity() < 1) {
                throw BusinessException.badRequest("Số lượng phải lớn hơn 0");
            }

            Sku sku = skusById.get(line.getSkuId());
            if (sku == null || sku.getProduct() == null) {
                throw BusinessException.notFound(
                        "Không tìm thấy biến thể sản phẩm (ID " + line.getSkuId() + ")");
            }

            ProductStatus productStatus = sku.getProduct().getStatus();
            if (productStatus != ProductStatus.ACTIVE) {
                throw BusinessException.conflict(
                        "Sản phẩm \"" + sku.getProduct().getName()
                                + "\" đang ngừng bán hoặc đã xoá, không thể đặt hàng");
            }

            int stock = sku.getStock() == null ? 0 : sku.getStock();
            int quantity = line.getQuantity();
            if (quantity > stock) {
                throw BusinessException.conflict(
                        "Biến thể \"" + describeSku(sku) + "\" chỉ còn " + stock
                                + " trong kho, không đủ " + quantity);
            }

            BigDecimal unitPrice = sku.getPrice() == null ? BigDecimal.ZERO : sku.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(lineTotal);

            items.add(OrderItem.builder()
                    .skuId(sku.getId())
                    .skuCode(sku.getSkuCode())
                    .productName(sku.getProduct().getName())
                    .variant(sku.getVariant())
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .totalPrice(lineTotal)
                    .build());
        }

        // Upsert customer by phone
        Customer customer = customerRepository
                .findByShopIdAndPhone(tenantId, req.getCustomerPhone())
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .shop(shop)
                        .fullName(req.getCustomerName())
                        .phone(req.getCustomerPhone())
                        .address(req.getCustomerAddress())
                        .build()));

        BigDecimal shippingFee = safeMoney(req.getShippingFee());
        BigDecimal discount = safeMoney(req.getDiscount());
        BigDecimal total = subtotal.add(shippingFee).subtract(discount);

        Order order = Order.builder()
                .shop(shop)
                .customer(customer)
                .shippingAddress(req.getCustomerAddress())
                .shippingFee(shippingFee)
                .discount(discount)
                .subtotal(subtotal)
                .totalAmount(total)
                .notes(req.getNotes())
                .orderedAt(LocalDateTime.now())
                .build();

        items.forEach(item -> {
            item.setOrder(order);
            order.getItems().add(item);
        });

        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse updateStatus(Long id, UpdateStatusRequest req) {
        Long tenantId = requireTenant();

        if (req.getStatus() == null) {
            throw BusinessException.badRequest("Trạng thái không được để trống");
        }

        Order order = findOrderForUpdate(tenantId, id);
        OrderStatus current = order.getStatus();
        OrderStatus target = req.getStatus();

        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(OrderStatus.class));
        if (!allowed.contains(target)) {
            throw BusinessException.badRequest(
                    "Không thể chuyển đơn từ \"" + statusLabel(current)
                            + "\" sang \"" + statusLabel(target) + "\"");
        }

        if (target == OrderStatus.CONFIRMED) {
            deductStock(tenantId, order);
        } else if (target == OrderStatus.CANCELLED && STOCK_DEDUCTED_STATUSES.contains(current)) {
            restoreStock(tenantId, order);
        }

        order.setStatus(target);
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional
    public void deleteOrder(Long id) {
        Long tenantId = requireTenant();
        Order order = findOrderForUpdate(tenantId, id);

        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw BusinessException.badRequest("Không thể huỷ đơn hàng đã giao");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }

        if (STOCK_DEDUCTED_STATUSES.contains(order.getStatus())) {
            restoreStock(tenantId, order);
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    // ---------------------------------------------------------------- HELPER

    private Long requireTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw BusinessException.forbidden("Không xác định được cửa hàng");
        }
        return tenantId;
    }

    private Order findOrder(Long tenantId, Long id) {
        return orderRepository.findByIdAndShopId(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng"));
    }

    private Order findOrderForUpdate(Long tenantId, Long id) {
        return orderRepository.findForUpdate(id, tenantId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng"));
    }

    private static void assertNoDuplicateSkus(List<CreateOrderRequest.OrderItemRequest> items) {
        Set<Long> seen = new HashSet<>();
        for (CreateOrderRequest.OrderItemRequest item : items) {
            if (item.getSkuId() == null) {
                throw BusinessException.badRequest(
                        "Vui lòng chọn sản phẩm cho tất cả các dòng");
            }
            if (!seen.add(item.getSkuId())) {
                throw BusinessException.badRequest(
                        "Mỗi biến thể chỉ được chọn một lần trong đơn hàng");
            }
        }
    }

    private Map<Long, Sku> loadSkus(Long tenantId, List<Long> skuIds) {
        return skuRepository.findByShopIdAndIdIn(tenantId, skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * Trừ tồn kho khi đơn được xác nhận. Khoá biến thể theo tenant, kiểm tra
     * toàn bộ trước khi ghi để tránh trừ một nửa rồi rollback.
     */
    private void deductStock(Long tenantId, Order order) {
        Map<Long, Integer> quantities = quantitiesBySku(order);
        if (quantities.isEmpty()) {
            return;
        }

        Map<Long, Sku> skusById = loadSkusForUpdate(tenantId, quantities.keySet());

        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Sku sku = skusById.get(entry.getKey());
            if (sku == null) {
                throw BusinessException.conflict(
                        "Không tìm thấy biến thể (ID " + entry.getKey() + ") để trừ tồn kho");
            }

            int stock = sku.getStock() == null ? 0 : sku.getStock();
            if (stock < entry.getValue()) {
                throw BusinessException.conflict(
                        "Không đủ tồn kho cho \"" + describeSku(sku) + "\": còn " + stock
                                + ", cần " + entry.getValue()
                                + ". Vui lòng cập nhật lại số lượng trước khi xác nhận.");
            }
        }

        quantities.forEach((skuId, quantity) -> {
            Sku sku = skusById.get(skuId);
            sku.setStock(sku.getStock() - quantity);
        });
    }

    /** Hoàn tồn kho khi huỷ đơn đã trừ tồn. */
    private void restoreStock(Long tenantId, Order order) {
        Map<Long, Integer> quantities = quantitiesBySku(order);
        if (quantities.isEmpty()) {
            return;
        }

        Map<Long, Sku> skusById = loadSkusForUpdate(tenantId, quantities.keySet());
        quantities.forEach((skuId, quantity) -> {
            Sku sku = skusById.get(skuId);
            if (sku != null) {
                int stock = sku.getStock() == null ? 0 : sku.getStock();
                sku.setStock(stock + quantity);
            }
        });
    }

    private Map<Long, Sku> loadSkusForUpdate(Long tenantId, Set<Long> skuIds) {
        return skuRepository.findForUpdate(tenantId, skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, Function.identity(), (a, b) -> a));
    }

    /** Đơn cũ (chưa gắn SKU) trả map rỗng để bỏ qua thao tác tồn kho. */
    private static Map<Long, Integer> quantitiesBySku(Order order) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            if (item.getSkuId() == null) {
                continue;
            }
            quantities.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
        }
        return quantities;
    }

    private static String describeSku(Sku sku) {
        StringBuilder label = new StringBuilder();
        if (sku.getProduct() != null) {
            label.append(sku.getProduct().getName());
        }
        if (sku.getVariant() != null && !sku.getVariant().isBlank()) {
            if (label.length() > 0) {
                label.append(" - ");
            }
            label.append(sku.getVariant());
        }
        if (label.length() == 0) {
            label.append(sku.getSkuCode());
        }
        return label.toString();
    }

    private static String statusLabel(OrderStatus status) {
        return STATUS_LABELS.getOrDefault(status, status.name());
    }

    private static BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
