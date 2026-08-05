package com.perimity.auth.messaging;

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
 * The account-lifecycle queue topology. Declared here because auth-service is
 * the producer, and the producer owns the contract - the same rule
 * gatepass-service follows for the QR exchange.
 *
 *   perimity.user  (direct exchange)
 *     └── user.created   -> user-service consumes
 *
 *   perimity.user.dlx (dead letter)
 *     └── user.created.dlq
 *
 * ==========================================================================
 * WHY THIS EXCHANGE EXISTS AT ALL
 * ==========================================================================
 * An account lives in auth-service; a STUDENT's or FACULTY's profile lives in
 * user-service. They are two records in two databases and, until now, the only
 * thing that ever created the second one was a React screen making a second
 * API call after the first succeeded.
 *
 * That is a dual write with no coordination, performed by a browser. If the
 * second call failed - network drop, closed tab, a role created from any other
 * screen - the account existed and the profile did not. The person could sign
 * in and then found every profile page telling them they did not exist, with no
 * way for them or anyone else to fix it. Several accounts on this system are in
 * exactly that state.
 *
 * Moving it to an event makes the failure recoverable instead of silent: the
 * broker retries, and anything that still fails lands in a DLQ where somebody
 * can see it. A queued message is a problem you can find; a browser that closed
 * mid-sequence is not.
 *
 * Why a dead-letter queue rather than endless retry: one malformed message that
 * always fails would be redelivered forever and starve everything behind it.
 * Same reasoning as the QR exchange.
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

    /** Durable, so a broker restart does not drop pending provisioning work. */
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

    /*
     * These two beans are named to match the ones qr-service and
     * gatepass-service declare. auth-service has no other AMQP config, so there
     * is nothing here to collide with.
     */
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
