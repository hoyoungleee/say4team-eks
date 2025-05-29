package com.playdata.orderingservice.common.dto;

import com.playdata.orderingservice.ordering.entity.Order;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter@Setter@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderNotificationEvent {
    private Long orderId; // 주문 ID (관리자가 주문을 찾기 위해)
    private String customerEmail;  // 고객 이메일 (누가 주문했는지)
    private Long customerId;  // 고객 ID (추가 조회용)
    private String orderStatus;  // 주문 상태 (ORDERED, CANCELED 등)
    private int totalItems;  // 총 상품 개수 (간단한 요약 정보)
    private LocalDateTime orderTime; // 주문 시간 (언제 주문했는지)
    private List<OrderItemInfo> orderItems; // 주문 상품 목록 (상세 정보)

    @Getter@Setter@ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemInfo{
        private Long productId;
        private int quantity;
    }

    // Ordering 엔티티에서 이벤트 생성
    public static OrderNotificationEvent fromOrdering(Order ordering) {
        // OrderDetail 리스트를 OrderItemInfo 리스트로 변환
        List<OrderItemInfo> items = ordering.getOrderItems().stream()
                .map(detail -> OrderItemInfo.builder()
                        .productId(detail.getProductId())
                        .quantity(detail.getQuantity())
                        .build())
                .toList();

        return OrderNotificationEvent.builder()
                .orderId(ordering.getOrderId())
                .customerEmail(ordering.getEmail())
//                .customerId(ordering.getUserId())
                .orderStatus(ordering.getOrderStatus().name())
                .totalItems(items.stream().mapToInt(OrderItemInfo::getQuantity).sum())
                .orderTime(LocalDateTime.now())
                .orderItems(items)
                .build();
    }
}
