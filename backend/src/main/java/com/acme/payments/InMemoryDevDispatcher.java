package com.acme.payments;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @org.springframework.context.annotation.Profile("dev")
public class InMemoryDevDispatcher {
    private final OutboxRepository events; private final JdbcTemplate jdbc;
    public InMemoryDevDispatcher(OutboxRepository events,JdbcTemplate jdbc){this.events=events;this.jdbc=jdbc;}
    @Scheduled(fixedDelayString="${payments.outbox.poll-ms:350}") @Transactional
    public void dispatch(){
        for(var event:events.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()){
            var p=jdbc.queryForMap("SELECT client_id,idempotency_key,description FROM payments WHERE id=?",event.aggregateId);
            String result=p.get("description").toString().toLowerCase().contains("decline")?"DECLINED":"APPROVED";
            String gatewayKey=p.get("client_id")+":"+p.get("idempotency_key");
            jdbc.update("MERGE INTO gateway_charges (gateway_key,payment_id,result) KEY(gateway_key) VALUES (?,?,?)",gatewayKey,event.aggregateId,result);
            String stored=jdbc.queryForObject("SELECT result FROM gateway_charges WHERE gateway_key=?",String.class,gatewayKey);
            jdbc.update("UPDATE payments SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('PENDING','PROCESSING','CREATED')",stored,event.aggregateId);
            event.publishedAt=java.time.Instant.now();events.save(event);
        }
    }
}
