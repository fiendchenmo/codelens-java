package com.example.payment;

import java.util.List;
import java.util.ArrayList;

public class PaymentService {

    private PaymentRepository paymentRepo;
    private NotificationService notificationService;
    private AuditLogger auditLogger;

    public PaymentResult processPayment(PaymentRequest request) {
        auditLogger.log("processPayment start", request.getOrderId());
        
        PaymentValidationResult validation = validatePayment(request);
        if (!validation.isValid()) {
            return PaymentResult.failed(validation.getErrorMessage());
        }

        Payment payment = paymentRepo.findByOrderId(request.getOrderId());
        if (payment != null && payment.isPaid()) {
            return PaymentResult.duplicate("订单已支付");
        }

        payment = createPayment(request);
        paymentRepo.save(payment);

        notificationService.notifyPaymentSuccess(request.getUserId(), payment.getId());
        auditLogger.log("processPayment end", request.getOrderId());

        return PaymentResult.success(payment);
    }

    private PaymentValidationResult validatePayment(PaymentRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getAmount() <= 0) {
            errors.add("金额必须大于0");
        }
        if (request.getUserId() == null) {
            errors.add("用户ID不能为空");
        }
        
        return new PaymentValidationResult(errors);
    }

    private Payment createPayment(PaymentRequest request) {
        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setAmount(request.getAmount());
        payment.setUserId(request.getUserId());
        payment.setStatus("PAID");
        return payment;
    }

    public List<Payment> queryByUserId(String userId) {
        return paymentRepo.findByUserId(userId);
    }

    public void refund(String paymentId) {
        Payment payment = paymentRepo.findById(paymentId);
        payment.setStatus("REFUNDED");
        paymentRepo.save(payment);
        notificationService.notifyRefund(payment.getUserId(), paymentId);
        auditLogger.log("refund", paymentId);
    }
}
