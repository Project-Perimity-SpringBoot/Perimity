package com.perimity.gatepass.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The queue topology, declared here because gatepass-service is the producer
 * and the producer owns the contract.
 *
 *   perimity.qr  (direct exchange)
 *     ├── qr.generate.request   -> qr-service consumes
 *     └── qr.generate.result    -> gatepass-service consumes
 *
 *   perimity.qr.dlx (dead letter)
 *     └── qr.generate.dlq
 *
 * Why a dead-letter queue and not endless retry: one malformed message that
 * always fails would otherwise be redelivered forever, pinning a consumer at
 * 100% CPU and starving every other job behind it. After three attempts the
 * message goes to the DLQ where a human can look at it, and the queue keeps
 * moving. That is the same "never let one bad row block the batch" rule the
 * bulk engine and the expiry sweep already follow.
 *
 * Queues are declared durable so a broker restart does not lose 600 pending
 * jobs. Messages are published persistent by default with the JSON converter.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "perimity.qr";
    public static final String DLX = "perimity.qr.dlx";

    public static final String QUEUE_GENERATE = "qr.generate.request";
    public static final String QUEUE_RESULT = "qr.generate.result";
    public static final String QUEUE_DLQ = "qr.generate.dlq";

    public static final String RK_GENERATE = "qr.generate";
    public static final String RK_RESULT = "qr.result";
    public static final String RK_DLQ = "qr.dead";

    @Bean
    DirectExchange qrExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange qrDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue qrGenerateQueue() {
        return QueueBuilder.durable(QUEUE_GENERATE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(RK_DLQ)
                .build();
    }

    @Bean
    Queue qrResultQueue() {
        return QueueBuilder.durable(QUEUE_RESULT)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(RK_DLQ)
                .build();
    }

    @Bean
    Queue qrDeadLetterQueue() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    @Bean
    Binding bindGenerate(Queue qrGenerateQueue, DirectExchange qrExchange) {
        return BindingBuilder.bind(qrGenerateQueue).to(qrExchange).with(RK_GENERATE);
    }

    @Bean
    Binding bindResult(Queue qrResultQueue, DirectExchange qrExchange) {
        return BindingBuilder.bind(qrResultQueue).to(qrExchange).with(RK_RESULT);
    }

    @Bean
    Binding bindDlq(Queue qrDeadLetterQueue, DirectExchange qrDeadLetterExchange) {
        return BindingBuilder.bind(qrDeadLetterQueue).to(qrDeadLetterExchange).with(RK_DLQ);
    }

    /** JSON on the wire, so a Python or Node consumer could read it too. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory factory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(converter);
        return template;
    }
}
