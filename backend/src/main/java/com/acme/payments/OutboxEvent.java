package com.acme.payments;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="outbox_events")
public class OutboxEvent {
    @Id public UUID id;
    @Column(name="aggregate_id", nullable=false) public UUID aggregateId;
    @Column(name="event_type", nullable=false) public String eventType;
    @Column(nullable=false, columnDefinition="text") public String payload;
    @Column(nullable=false) public Instant createdAt;
    @Column public Instant publishedAt;
    protected OutboxEvent() {}
}