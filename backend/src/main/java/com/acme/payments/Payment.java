package com.acme.payments;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="payments")
public class Payment {
    @Id public UUID id;
    @Column(name="client_id", nullable=false) public String clientId;
    @Column(name="idempotency_key", nullable=false) public String idempotencyKey;
    @Column(name="request_hash", nullable=false, length=64) public String requestHash;
    @Column(nullable=false, precision=19, scale=4) public BigDecimal amount;
    @Column(nullable=false, length=3) public String currency;
    @Column(nullable=false) public String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public Status status;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="updated_at", nullable=false) public Instant updatedAt;
    protected Payment() {}
    public enum Status { CREATED, PENDING, PROCESSING, APPROVED, DECLINED, FAILED, REFUNDED }
}