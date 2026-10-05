package com.acme.payments;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Component @org.springframework.context.annotation.Profile("!dev")
public class OutboxPublisher {
    private final OutboxRepository events; private final RabbitTemplate rabbit;
    public OutboxPublisher(OutboxRepository events,RabbitTemplate rabbit){this.events=events;this.rabbit=rabbit;}
    @Scheduled(fixedDelayString="${payments.outbox.poll-ms:1000}")
    @Transactional
    public void publishPending(){
        for(var event:events.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()){
            var confirmation=new org.springframework.amqp.rabbit.connection.CorrelationData(event.id.toString());
            rabbit.convertAndSend(MessagingConfig.EXCHANGE,MessagingConfig.ROUTING,event.payload,m->{m.getMessageProperties().setMessageId(event.id.toString());m.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);return m;},confirmation);
            try {
                var confirm=confirmation.getFuture().get(10,java.util.concurrent.TimeUnit.SECONDS);
                if(!confirm.ack())throw new IllegalStateException("RabbitMQ rejected outbox event: "+confirm.reason());
            } catch(Exception failure) { throw new IllegalStateException("RabbitMQ publisher confirmation failed",failure); }
            event.publishedAt=Instant.now(); events.save(event);
        }
    }
}
