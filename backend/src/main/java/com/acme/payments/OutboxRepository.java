package com.acme.payments;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> { List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc(); }
