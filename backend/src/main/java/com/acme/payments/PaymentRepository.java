package com.acme.payments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByClientIdAndIdempotencyKey(String clientId, String key);
    List<Payment> findAllByClientIdOrderByCreatedAtDesc(String clientId);

    @Query(value = "select p from Payment p where p.clientId = :clientId " +
            "and (:status is null or p.status = :status) " +
            "and (:fromInclusive is null or p.createdAt >= :fromInclusive) " +
            "and (:toExclusive is null or p.createdAt < :toExclusive)",
            countQuery = "select count(p) from Payment p where p.clientId = :clientId " +
                    "and (:status is null or p.status = :status) " +
                    "and (:fromInclusive is null or p.createdAt >= :fromInclusive) " +
                    "and (:toExclusive is null or p.createdAt < :toExclusive)")
    Page<Payment> searchForClient(@Param("clientId") String clientId,
                                  @Param("status") Payment.Status status,
                                  @Param("fromInclusive") Instant fromInclusive,
                                  @Param("toExclusive") Instant toExclusive,
                                  Pageable pageable);
}
