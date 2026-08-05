package com.perimity.user.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The consumer's view of the account-lifecycle topology.
 *
 * auth-service is the producer and owns the contract; these declarations must
 * match its RabbitConfig EXACTLY. Declaring a durable queue with different
 * arguments than the one already on the broker is not a warning - RabbitMQ
 * refuses the declaration outright and the listener never starts.
 *
 * Declared on both sides on purpose, the same way qr-service and
 * gatepass-service both declare the QR topology: whichever service boots first
 * creates the queue, so neither has to be started before the other.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "perimity.user";
    public static final String DLX = "perimity.user.dlx";

    public static final String QUEUE_USER_CREATED = "user.created";
    public static final String QUEUE_DLQ = "user.created.dlq";

    public static final String RK_USER_CREATED = "user.created";
    public static final String RK_DLQ = "user.dead";

    @Bean
    DirectExchange userExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange userDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue userCreatedQueue() {
        return QueueBuilder.durable(QUEUE_USER_CREATED)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(RK_DLQ)
                .build();
    }

    @Bean
    Queue userDeadLetterQueue() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    @Bean
    Binding bindUserCreated(Queue userCreatedQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userCreatedQueue).to(userExchange).with(RK_USER_CREATED);
    }

    @Bean
    Binding bindUserDlq(Queue userDeadLetterQueue, DirectExchange userDeadLetterExchange) {
        return BindingBuilder.bind(userDeadLetterQueue).to(userDeadLetterExchange).with(RK_DLQ);
    }

    /**
     * JSON on the wire. The producer sends with the same converter, so the
     * listener can take a typed record parameter rather than a byte array.
     */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
