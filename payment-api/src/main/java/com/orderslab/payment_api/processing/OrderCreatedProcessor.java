package com.orderslab.payment_api.processing;

import com.orderslab.payment_api.config.KafkaTopicsConfig;
import com.orderslab.payment_api.event.OrderCreatedMessage;
import com.orderslab.payment_api.event.PaymentFailed;
import com.orderslab.payment_api.event.PaymentReserved;
import com.orderslab.payment_api.model.Payment;
import com.orderslab.payment_api.model.PaymentStatus;
import com.orderslab.payment_api.outbox.OutboxWriter;
import com.orderslab.payment_api.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trata um OrderCreated em uma única transação: checa duplicata, grava o pagamento decidido e o
 * evento de saída no outbox. O pagamento (com source_event_id) é o marcador de "evento tratado".
 */
@Component
public class OrderCreatedProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedProcessor.class);

    private final PaymentRepository paymentRepository;
    private final PaymentDecisionPolicy policy;
    private final OutboxWriter outboxWriter;

    public OrderCreatedProcessor(PaymentRepository paymentRepository, PaymentDecisionPolicy policy,
                                 OutboxWriter outboxWriter) {
        this.paymentRepository = paymentRepository;
        this.policy = policy;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public void process(OrderCreatedMessage message) {
        String orderId = message.orderId().toString();
        if (paymentRepository.existsBySourceEventId(message.eventId())
                || paymentRepository.existsByOrderIdAndSourceEventIdIsNotNull(orderId)) {
            log.atInfo()
                    .addKeyValue("orderId", message.orderId())
                    .addKeyValue("eventId", message.eventId())
                    .addKeyValue("eventType", "OrderCreated")
                    .log("Duplicate OrderCreated ignored");
            return;
        }

        PaymentDecisionPolicy.Decision decision = policy.decide(message.amount());
        Payment payment = Payment.fromEvent(orderId, message.amount(), message.eventId(), decision.status());
        paymentRepository.save(payment);

        String outputType;
        if (decision.status() == PaymentStatus.RESERVED) {
            outputType = PaymentReserved.TYPE;
            outboxWriter.enqueue(PaymentReserved.of(message.orderId(), payment.getId(), message.amount(),
                    payment.getCreatedAt()), KafkaTopicsConfig.PAYMENT_RESERVED);
        } else {
            outputType = PaymentFailed.TYPE;
            outboxWriter.enqueue(PaymentFailed.of(message.orderId(), decision.reason(), payment.getCreatedAt()),
                    KafkaTopicsConfig.PAYMENT_FAILED);
        }
        log.atInfo()
                .addKeyValue("orderId", message.orderId())
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("eventType", outputType)
                .addKeyValue("paymentId", payment.getId())
                .addKeyValue("status", payment.getStatus())
                .log("Payment decided from OrderCreated");
    }
}
