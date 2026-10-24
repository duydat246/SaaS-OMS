package com.saasoms.modules.order.service;

import com.saasoms.common.exception.BusinessException;
import com.saasoms.common.tenant.TenantContext;
import com.saasoms.modules.customer.entity.Customer;
import com.saasoms.modules.customer.repository.CustomerRepository;
import com.saasoms.modules.order.dto.CreateOrderRequest;
import com.saasoms.modules.order.dto.OrderResponse;
import com.saasoms.modules.order.dto.UpdateStatusRequest;
import com.saasoms.modules.order.entity.Order;
import com.saasoms.modules.order.entity.OrderItem;
import com.saasoms.modules.order.entity.OrderStatus;
import com.saasoms.modules.order.repository.OrderRepository;
import com.saasoms.modules.product.entity.Product;
import com.saasoms.modules.product.entity.ProductStatus;
import com.saasoms.modules.product.entity.Sku;
import com.saasoms.modules.product.repository.SkuRepository;
import com.saasoms.modules.shop.entity.Shop;
import com.saasoms.modules.shop.repository.ShopRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long SKU_ID = 10L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private SkuRepository skuRepository;

    @InjectMocks
    private OrderService orderService;

    private Shop shop;
    private Customer customer;
    private Product product;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        shop = Shop.builder()
                .id(TENANT_ID)
                .name("Shop test")
                .slug("shop-test")
                .platform("MANUAL")
                .build();
        customer = Customer.builder()
                .id(5L)
                .shop(shop)
                .fullName("Nguyễn Văn A")
                .phone("0901234567")
                .build();
        product = Product.builder()
                .id(7L)
                .shop(shop)
                .name("Áo thun")
                .status(ProductStatus.ACTIVE)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // --------------------------------------------------------------- CREATE

    @Test
    void createOrder_snapshotsSkuAndComputesTotalsFromSku() {
        Sku sku = sku(5, "100000.00");
        CreateOrderRequest request = request(2);

        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(skuRepository.findByShopIdAndIdIn(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of(sku));
        when(customerRepository.findByShopIdAndPhone(TENANT_ID, "0901234567"))
                .thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.getOrderedAt()).isNotNull();
        assertThat(response.getSubtotal()).isEqualByComparingTo("200000.00");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("215000.00");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getSkuId()).isEqualTo(SKU_ID);
        assertThat(response.getItems().get(0).getSkuCode()).isEqualTo("SKU-1");
        assertThat(response.getItems().get(0).getProductName()).isEqualTo("Áo thun");
        assertThat(response.getItems().get(0).getUnitPrice()).isEqualByComparingTo("100000.00");
        assertThat(response.getItems().get(0).getTotalPrice()).isEqualByComparingTo("200000.00");
    }

    @Test
    void createOrder_rejectsQuantityGreaterThanStock() {
        Sku sku = sku(5, "100000.00");
        CreateOrderRequest request = request(6);

        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(skuRepository.findByShopIdAndIdIn(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of(sku));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("5");
        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(customerRepository);
    }

    @Test
    void createOrder_returnsNotFoundForSkuOfAnotherTenant() {
        CreateOrderRequest request = request(1);

        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(skuRepository.findByShopIdAndIdIn(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_rejectsInactiveProduct() {
        product.setStatus(ProductStatus.INACTIVE);
        Sku sku = sku(5, "100000.00");
        CreateOrderRequest request = request(1);

        when(shopRepository.findById(TENANT_ID)).thenReturn(Optional.of(shop));
        when(skuRepository.findByShopIdAndIdIn(eq(TENANT_ID), anyCollection()))
                .thenReturn(List.of(sku));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(request));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(orderRepository, never()).save(any(Order.class));
    }

    // ------------------------------------------------------- STOCK ON CONFIRM

    @Test
    void updateStatus_confirmDeductsStock() {
        Sku sku = sku(10, "100000.00");
        Order order = order(OrderStatus.PENDING, 3);

        when(orderRepository.findForUpdate(100L, TENANT_ID)).thenReturn(Optional.of(order));
        when(skuRepository.findForUpdate(eq(TENANT_ID), anyCollection())).thenReturn(List.of(sku));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.updateStatus(100L, statusRequest(OrderStatus.CONFIRMED));

        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        assertThat(sku.getStock()).isEqualTo(7);
    }

    @Test
    void updateStatus_confirmRejectsWhenStockNoLongerEnough() {
        Sku sku = sku(2, "100000.00");
        Order order = order(OrderStatus.PENDING, 3);

        when(orderRepository.findForUpdate(100L, TENANT_ID)).thenReturn(Optional.of(order));
        when(skuRepository.findForUpdate(eq(TENANT_ID), anyCollection())).thenReturn(List.of(sku));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.updateStatus(100L, statusRequest(OrderStatus.CONFIRMED)));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(sku.getStock()).isEqualTo(2);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(order);
    }

    @Test
    void updateStatus_cancelRestoresDeductedStock() {
        Sku sku = sku(7, "100000.00");
        Order order = order(OrderStatus.CONFIRMED, 3);

        when(orderRepository.findForUpdate(100L, TENANT_ID)).thenReturn(Optional.of(order));
        when(skuRepository.findForUpdate(eq(TENANT_ID), anyCollection())).thenReturn(List.of(sku));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.updateStatus(100L, statusRequest(OrderStatus.CANCELLED));

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(sku.getStock()).isEqualTo(10);
    }

    @Test
    void updateStatus_cancelPendingDoesNotTouchStock() {
        Order order = order(OrderStatus.PENDING, 3);

        when(orderRepository.findForUpdate(100L, TENANT_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.updateStatus(100L, statusRequest(OrderStatus.CANCELLED));

        verify(skuRepository, never()).findForUpdate(any(), anyCollection());
    }

    @Test
    void updateStatus_rejectsInvalidTransition() {
        Order order = order(OrderStatus.PENDING, 1);

        when(orderRepository.findForUpdate(100L, TENANT_ID)).thenReturn(Optional.of(order));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.updateStatus(100L, statusRequest(OrderStatus.DELIVERED)));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(skuRepository);
    }

    // ---------------------------------------------------------------- HELPERS

    private Sku sku(int stock, String price) {
        return Sku.builder()
                .id(SKU_ID)
                .product(product)
                .shop(shop)
                .skuCode("SKU-1")
                .variant("Đỏ / L")
                .price(new BigDecimal(price))
                .stock(stock)
                .build();
    }

    private CreateOrderRequest request(int quantity) {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setSkuId(SKU_ID);
        item.setQuantity(quantity);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("Nguyễn Văn A");
        request.setCustomerPhone("0901234567");
        request.setCustomerAddress("123 Đường, Thành phố");
        request.setShippingFee(new BigDecimal("20000.00"));
        request.setDiscount(new BigDecimal("5000.00"));
        request.setItems(List.of(item));
        return request;
    }

    private Order order(OrderStatus status, int quantity) {
        Order order = Order.builder()
                .id(100L)
                .shop(shop)
                .customer(customer)
                .status(status)
                .subtotal(new BigDecimal("100000.00"))
                .shippingFee(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100000.00"))
                .build();

        order.getItems().add(OrderItem.builder()
                .id(1L)
                .skuId(SKU_ID)
                .skuCode("SKU-1")
                .productName("Áo thun")
                .variant("Đỏ / L")
                .quantity(quantity)
                .unitPrice(new BigDecimal("100000.00"))
                .totalPrice(new BigDecimal("100000.00"))
                .build());
        return order;
    }

    private UpdateStatusRequest statusRequest(OrderStatus status) {
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus(status);
        return request;
    }
}
