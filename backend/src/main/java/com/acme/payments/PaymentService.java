package com.acme.payments;

import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    public PaymentService(PaymentRepository payments, OutboxRepository outbox, ObjectMapper mapper, JdbcTemplate jdbc) { this.payments=payments; this.outbox=outbox; this.mapper=mapper; this.jdbc=jdbc; }

    @Transactional
    public Payment create(String clientId, String key, CreatePayment request) {
        boolean h2 = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()));
        if (h2) jdbc.queryForObject("SELECT lock_name FROM dev_locks WHERE lock_name=? FOR UPDATE",String.class,"payment-idempotency");
        String hash = hash(request);
        var prior = payments.findByClientIdAndIdempotencyKey(clientId, key);
        if (prior.isPresent()) return replay(prior.get(), hash);
        Payment p = new Payment(); p.id=UUID.randomUUID(); p.clientId=clientId; p.idempotencyKey=key; p.requestHash=hash;
        p.amount=request.amount(); p.currency=request.currency().toUpperCase(Locale.ROOT); p.description=request.description(); p.status=Payment.Status.PENDING; p.createdAt=Instant.now(); p.updatedAt=p.createdAt;
        try {
            if (h2) {
                jdbc.update("""
                    MERGE INTO payments target
                    USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)) source(id,client_id,idempotency_key,request_hash,amount,currency,description,status,created_at,updated_at)
                    ON target.client_id=source.client_id AND target.idempotency_key=source.idempotency_key
                    WHEN MATCHED THEN UPDATE SET id=target.id
                    WHEN NOT MATCHED THEN INSERT(id,client_id,idempotency_key,request_hash,amount,currency,description,status,created_at,updated_at)
                    VALUES(source.id,source.client_id,source.idempotency_key,source.request_hash,source.amount,source.currency,source.description,source.status,source.created_at,source.updated_at)
                    """, p.id, p.clientId, p.idempotencyKey, p.requestHash, p.amount, p.currency, p.description, p.status.name(), p.createdAt, p.updatedAt);
                var stored = payments.findByClientIdAndIdempotencyKey(clientId,key).orElseThrow();
                if (!stored.id.equals(p.id)) return replay(stored,hash);
            } else {
                int inserted = jdbc.update("""
                    INSERT INTO payments (id, client_id, idempotency_key, request_hash, amount, currency, description, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (client_id, idempotency_key) DO NOTHING
                    """, p.id, p.clientId, p.idempotencyKey, p.requestHash, p.amount, p.currency, p.description, p.status.name(), p.createdAt, p.updatedAt);
                if (inserted == 0) {
                    // PostgreSQL waits for an in-flight conflicting insert, then exposes its committed row here.
                    return replay(payments.findByClientIdAndIdempotencyKey(clientId, key).orElseThrow(), hash);
                }
            }
            OutboxEvent e = new OutboxEvent(); e.id=UUID.randomUUID(); e.aggregateId=p.id; e.eventType="PaymentRequested"; e.createdAt=Instant.now();
            e.payload=mapper.writeValueAsString(Map.of("paymentId",p.id,"clientId",clientId,"idempotencyKey",key)); outbox.save(e);
            return p;
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Could not persist payment intent",e); }
    }
    private Payment replay(Payment p, String hash) { if (!MessageDigest.isEqual(p.requestHash.getBytes(StandardCharsets.US_ASCII),hash.getBytes(StandardCharsets.US_ASCII))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency-Key reused with different request body"); return p; }
    private String hash(CreatePayment r) { try { var md=MessageDigest.getInstance("SHA-256"); var canonical=String.join("|",r.amount().stripTrailingZeros().toPlainString(),r.currency().toUpperCase(Locale.ROOT),r.description()); return HexFormat.of().formatHex(md.digest(canonical.getBytes(StandardCharsets.UTF_8))); } catch(Exception e){ throw new IllegalStateException(e); } }
    public record CreatePayment(@jakarta.validation.constraints.DecimalMin(value="0.01") @jakarta.validation.constraints.Digits(integer=15,fraction=4) BigDecimal amount, @jakarta.validation.constraints.Pattern(regexp="[A-Za-z]{3}") String currency, @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=240) String description) {}
}

