package org.di.ai_investigator_report.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.di.ai_investigator_report.consumer.ReportProcessingConsumer;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.report.queue}")
    public String REPORT_QUEUE;

    @Value("${spring.rabbitmq.report.exchange}")
    public String REPORT_EXCHANGE;

    @Value("${spring.rabbitmq.report.routing-key}")
    public String REPORT_ROUTING_KEY;

    @Value("${spring.rabbitmq.report.result.queue}")
    public String REPORT_RESULT_QUEUE;

    @Value("${spring.rabbitmq.report.result.exchange}")
    public String REPORT_RESULT_EXCHANGE;

    @Value("${spring.rabbitmq.report.result.routing-key}")
    public String REPORT_RESULT_ROUTING_KEY;

    @Value("${spring.rabbitmq.dlq.exchange}")
    public String DLQ_EXCHANGE;

    @Value("${spring.rabbitmq.dlq.routing-key}")
    public String DLQ_ROUTING_KEY;

    @Bean
    public Queue reportQueue() {
        return QueueBuilder.durable(REPORT_QUEUE)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange reportExchange() {
        return new DirectExchange(REPORT_EXCHANGE);
    }

    @Bean
    public Binding reportBinding(Queue reportQueue, DirectExchange reportExchange) {
        return BindingBuilder
                .bind(reportQueue)
                .to(reportExchange)
                .with(REPORT_ROUTING_KEY);
    }

    @Bean
    public Queue reportResultQueue() {
        return QueueBuilder.durable(REPORT_RESULT_QUEUE).build();
    }

    @Bean
    public DirectExchange reportResultExchange() {
        return new DirectExchange(REPORT_RESULT_EXCHANGE);
    }

    @Bean
    public Binding reportResultBinding(Queue reportResultQueue, DirectExchange reportResultExchange) {
        return BindingBuilder
                .bind(reportResultQueue)
                .to(reportResultExchange)
                .with(REPORT_RESULT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter(
            ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public SimpleMessageListenerContainer reportListenerContainer(
            ConnectionFactory connectionFactory,
            ReportProcessingConsumer consumer) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(REPORT_QUEUE);
        container.setMessageListener(consumer);
        container.setDefaultRequeueRejected(false);
        container.setPrefetchCount(1);
        return container;
    }
}
