package com.acme.payments;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {
    public static final String EXCHANGE="payments.exchange", QUEUE="payments.process", ROUTING="payment.requested";
    @Bean DirectExchange paymentsExchange(){return new DirectExchange(EXCHANGE,true,false);}
    @Bean DirectExchange deadExchange(){return new DirectExchange("payments.dlx",true,false);}
    @Bean Queue paymentsQueue(){return QueueBuilder.durable(QUEUE).deadLetterExchange("payments.dlx").deadLetterRoutingKey("payment.dead").build();}
    @Bean Queue deadQueue(){return QueueBuilder.durable("payments.dlq").build();}
    @Bean Binding paymentBinding(){return BindingBuilder.bind(paymentsQueue()).to(paymentsExchange()).with(ROUTING);}
    @Bean Binding deadBinding(){return BindingBuilder.bind(deadQueue()).to(deadExchange()).with("payment.dead");}
}
