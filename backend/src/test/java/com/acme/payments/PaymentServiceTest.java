package com.acme.payments;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    @Test void replayReturnsOriginalPaymentWithoutSecondInsert() {
        var repo=mock(PaymentRepository.class); var outbox=mock(OutboxRepository.class);
        var jdbc=mock(JdbcTemplate.class); when(jdbc.execute(any(org.springframework.jdbc.core.ConnectionCallback.class))).thenReturn(false); var service=new PaymentService(repo,outbox,tools.jackson.databind.json.JsonMapper.builder().build(),jdbc);
        var body=new PaymentService.CreatePayment(new BigDecimal("12.50"),"BRL","Order 1");
        when(jdbc.update(anyString(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(1);
        var first=service.create("demo-user","order-12345678",body);
        when(repo.findByClientIdAndIdempotencyKey("demo-user","order-12345678")).thenReturn(Optional.of(first));
        var replay=service.create("demo-user","order-12345678",body);
        assertEquals(first.id,replay.id);
        verify(jdbc,times(1)).update(anyString(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
        verify(outbox,times(1)).save(any(OutboxEvent.class));
    }

    @Test void changedPayloadWithSameKeyConflicts() {
        var repo=mock(PaymentRepository.class); var jdbc=mock(JdbcTemplate.class);
        when(jdbc.execute(any(org.springframework.jdbc.core.ConnectionCallback.class))).thenReturn(false);
        var service=new PaymentService(repo,mock(OutboxRepository.class),tools.jackson.databind.json.JsonMapper.builder().build(),jdbc);
        var firstBody=new PaymentService.CreatePayment(new BigDecimal("12.50"),"BRL","Order 1");
        when(jdbc.update(anyString(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(1);
        var first=service.create("demo-user","order-12345678",firstBody);
        when(repo.findByClientIdAndIdempotencyKey("demo-user","order-12345678")).thenReturn(Optional.of(first));
        assertThrows(ResponseStatusException.class,()->service.create("demo-user","order-12345678",new PaymentService.CreatePayment(new BigDecimal("13"),"BRL","Order 1")));
    }
}


