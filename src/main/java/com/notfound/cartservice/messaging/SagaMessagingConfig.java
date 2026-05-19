package com.notfound.cartservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@EnableConfigurationProperties(SagaProperties.class)
@AutoConfigureAfter(RabbitAutoConfiguration.class)
@ConditionalOnProperty(prefix = "saga", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SagaMessagingConfig {

    @Bean
    public MessageConverter sagaMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter sagaMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(sagaMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    public TopicExchange commandsExchange(SagaProperties sagaProperties) {
        return new TopicExchange(sagaProperties.getExchangeCommands(), true, false);
    }

    @Bean
    public TopicExchange eventsExchange(SagaProperties sagaProperties) {
        return new TopicExchange(sagaProperties.getExchangeEvents(), true, false);
    }

    @Bean
    public Queue cartCommandsQueue(SagaProperties sagaProperties) {
        return QueueBuilder.durable(sagaProperties.getQueueCartCommands()).build();
    }

    @Bean
    public Binding cartClearCommandBinding(Queue cartCommandsQueue,
                                           TopicExchange commandsExchange,
                                           SagaProperties sagaProperties) {
        return BindingBuilder.bind(cartCommandsQueue)
                .to(commandsExchange)
                .with(sagaProperties.getRkCartClearCommand());
    }
}
