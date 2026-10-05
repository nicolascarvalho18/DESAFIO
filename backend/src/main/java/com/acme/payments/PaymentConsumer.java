package com.acme.payments;

import tools.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;

@Component @org.springframework.context.annotation.Profile("!dev")
public class PaymentConsumer {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper;
    public PaymentConsumer(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    @RabbitListener(queues=MessagingConfig.QUEUE)
    @Transactional
    public void process(org.springframework.amqp.core.Message message){
        UUID messageId=UUID.fromString(message.getMessageProperties().getMessageId());
        UUID paymentId;
        try { paymentId=UUID.fromString(mapper.readValue(message.getBody(),Map.class).get("paymentId").toString()); }
        catch(Exception e){throw new IllegalArgumentException("Invalid payment event",e);}
        if(jdbc.update("INSERT INTO processed_messages(message_id) VALUES (?) ON CONFLICT DO NOTHING",messageId)==0)return;
        var payment=jdbc.queryForMap("SELECT client_id,idempotency_key,description,status FROM payments WHERE id=? FOR UPDATE",paymentId);
        String gatewayKey=payment.get("client_id")+":"+payment.get("idempotency_key");
        String result=payment.get("description").toString().toLowerCase().contains("decline")?"DECLINED":"APPROVED";
        jdbc.update("INSERT INTO gateway_charges(gateway_key,payment_id,result) VALUES (?,?,?) ON CONFLICT(gateway_key) DO NOTHING",gatewayKey,paymentId,result);
        String stored=jdbc.queryForObject("SELECT result FROM gateway_charges WHERE gateway_key=?",String.class,gatewayKey);
        jdbc.update("UPDATE payments SET status=?,updated_at=now() WHERE id=? AND status IN ('PENDING','PROCESSING','CREATED')",stored,paymentId);
    }
}

