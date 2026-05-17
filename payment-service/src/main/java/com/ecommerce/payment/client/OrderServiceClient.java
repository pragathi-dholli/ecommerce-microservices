package com.ecommerce.payment.client;

import com.ecommerce.payment.dto.PaymentDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "order-service",
    url = "${services.order.url}",
    fallback = OrderServiceClient.OrderServiceFallback.class
)
public interface OrderServiceClient {

    @PostMapping("/api/v1/orders/payment-callback")
    void notifyPaymentResult(@RequestBody PaymentDto.OrderCallbackRequest callback);

    @org.springframework.stereotype.Component
    class OrderServiceFallback implements OrderServiceClient {

        @Override
        public void notifyPaymentResult(PaymentDto.OrderCallbackRequest callback) {
            // Circuit open — log for manual reconciliation
            org.slf4j.LoggerFactory.getLogger(OrderServiceFallback.class)
                .error("CALLBACK FAILED (circuit open) — orderNumber={}, status={}. Manual reconciliation required.",
                    callback.getOrderNumber(), callback.getPaymentStatus());
        }
    }
}
