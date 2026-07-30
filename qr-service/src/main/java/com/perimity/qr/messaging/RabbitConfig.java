package com.perimity.qr.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * ==========================================================================
 *  The topology below MUST stay identical to gatepass-service's RabbitConfig.
 *
 *  Every name, every durable flag, every dead-letter argument. RabbitMQ
 *  refuses to redeclare an existing queue with different arguments, and the
 *  refusal arrives as a channel-level error during startup rather than as a
 *  readable message - so if the two copies drift, BOTH services stop working
 *  and neither log says why.
 *
 *  Owned by gatepass-service (Tushar), the producer. Copied here.
 * ==========================================================================
 *
 *   perimity.qr  (direct exchange)
 *     |-- qr.generate.request   -> this service consumes
 *     |-- qr.generate.result    -> this service publishes, gatepass consumes
 *
 *   perimity.qr.dlx
 *     |-- qr.generate.dlq       -> poison messages land here
 *
 * Direct rather than fanout: fanout would deliver every generation job to the
 * result queue as well, so gatepass would receive jobs it cannot process.
 *
 * The converter and RabbitTemplate beans below are NOT part of the shared
 * contract - they are local wiring, and the two services may configure them
 * differently without consequence. Only the exchange, queue and binding
 * declarations are broker state that both sides have to agree on.
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

    /**
     * JSON on the wire.
     *
     * A COPY of the Boot ObjectMapper, with FAIL_ON_UNKNOWN_PROPERTIES
     * explicitly disabled. The copy matters twice over.
     *
     * Copy, because mutating the shared ObjectMapper would change how every
     * HTTP response in this service is serialised as a side effect of a
     * messaging setting - the kind of coupling that produces a bug three files
     * away from its cause.
     *
     * Explicit, because the producer's contract is deliberately wider than
     * anything this service reads. QrGenerationJob carries campusName,
     * holderEmail, emailSubject and more, all of which Day 8 ignores. Boot
     * already disables this feature by default, so the line is belt and
     * braces - but relying on a default for a cross-service contract means one
     * spring.jackson property added by anyone, for any reason, silently breaks
     * every message.
     *
     * Type precedence is left alone. DefaultJackson2JavaTypeMapper already
     * defaults to INFERRED, so the __TypeId__ header naming a
     * com.perimity.gatepass.* class is ignored in favour of the listener
     * method's own parameter type. (An earlier version of this file set that
     * explicitly and claimed it was necessary. It is not.)
     */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        ObjectMapper messagingMapper = objectMapper.copy()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return new Jackson2JsonMessageConverter(messagingMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory factory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(converter);
        return template;
    }
}
