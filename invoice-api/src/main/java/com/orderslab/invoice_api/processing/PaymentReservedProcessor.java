package com.orderslab.invoice_api.processing;

import com.orderslab.invoice_api.config.KafkaTopicsConfig;
import com.orderslab.invoice_api.event.InvoiceFailed;
import com.orderslab.invoice_api.event.InvoiceIssued;
import com.orderslab.invoice_api.event.PaymentReservedMessage;
import com.orderslab.invoice_api.model.Invoice;
import com.orderslab.invoice_api.model.InvoiceStatus;
import com.orderslab.invoice_api.outbox.OutboxWriter;
import com.orderslab.invoice_api.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trata um PaymentReserved em uma única transação: checa duplicata, grava a nota decidida e o
 * evento de saída no outbox. A nota (com source_event_id) é o marcador de "evento tratado".
 */
@Component
public class PaymentReservedProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentReservedProcessor.class);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceDecisionPolicy policy;
    private final OutboxWriter outboxWriter;

    public PaymentReservedProcessor(InvoiceRepository invoiceRepository, InvoiceDecisionPolicy policy,
                                    OutboxWriter outboxWriter) {
        this.invoiceRepository = invoiceRepository;
        this.policy = policy;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public void process(PaymentReservedMessage message) {
        String orderId = message.orderId().toString();
        if (invoiceRepository.existsBySourceEventId(message.eventId())
                || invoiceRepository.existsByOrderIdAndSourceEventIdIsNotNull(orderId)) {
            log.atInfo()
                    .addKeyValue("orderId", message.orderId())
                    .addKeyValue("eventId", message.eventId())
                    .addKeyValue("eventType", "PaymentReserved")
                    .log("Duplicate PaymentReserved ignored");
            return;
        }

        InvoiceDecisionPolicy.Decision decision = policy.decide(message.amount());
        Invoice invoice = Invoice.fromEvent(orderId, message.paymentId().toString(), message.amount(),
                message.eventId(), decision.status());
        invoiceRepository.save(invoice);

        String outputType;
        if (decision.status() == InvoiceStatus.ISSUED) {
            outputType = InvoiceIssued.TYPE;
            outboxWriter.enqueue(InvoiceIssued.of(message.orderId(), invoice.getId(), message.paymentId(),
                    message.amount(), invoice.getCreatedAt()), KafkaTopicsConfig.INVOICE_ISSUED);
        } else {
            outputType = InvoiceFailed.TYPE;
            outboxWriter.enqueue(InvoiceFailed.of(message.orderId(), message.paymentId(), decision.reason(),
                    invoice.getCreatedAt()), KafkaTopicsConfig.INVOICE_FAILED);
        }
        log.atInfo()
                .addKeyValue("orderId", message.orderId())
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("eventType", outputType)
                .addKeyValue("invoiceId", invoice.getId())
                .addKeyValue("status", invoice.getStatus())
                .log("Invoice decided from PaymentReserved");
    }
}
