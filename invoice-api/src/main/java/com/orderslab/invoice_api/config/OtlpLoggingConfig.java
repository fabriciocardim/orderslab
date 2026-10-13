package com.orderslab.invoice_api.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Instala o {@link OpenTelemetry} autoconfigurado pelo Spring Boot no appender Logback
 * declarado em {@code logback-spring.xml}, habilitando o export de logs via OTLP
 * (research.md, Decisão 5).
 */
@Component
class OtlpLoggingConfig implements InitializingBean {

    private final OpenTelemetry openTelemetry;

    OtlpLoggingConfig(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterPropertiesSet() {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
