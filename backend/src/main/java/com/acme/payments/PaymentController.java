package com.acme.payments;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {
    private final PaymentService service; private final PaymentRepository payments;
    public PaymentController(PaymentService service,PaymentRepository payments){this.service=service;this.payments=payments;}
    @PostMapping public ResponseEntity<Payment> create(@RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal Jwt principal,@Valid @RequestBody PaymentService.CreatePayment body){if(!key.matches("[A-Za-z0-9._:-]{8,128}"))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Idempotency-Key");var p=service.create(principal.getSubject(),key,body);return ResponseEntity.status(HttpStatus.ACCEPTED).body(p);}
    @GetMapping public PaymentPage list(@AuthenticationPrincipal Jwt principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Payment.Status status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        if (page < 0) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be zero or greater");
        if (size < 1 || size > 100) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        if (from != null && to != null && from.isAfter(to)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        var start = from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        var end = to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var results = payments.searchForClient(principal.getSubject(), status, start, end,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PaymentPage(results.getContent(), results.getNumber(), results.getSize(),
                results.getTotalElements(), results.getTotalPages(), results.isFirst(), results.isLast());
    }
    @GetMapping("/{id}") public Payment get(@PathVariable UUID id,@AuthenticationPrincipal Jwt principal){return payments.findById(id).filter(p->p.clientId.equals(principal.getSubject())).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));}
    @GetMapping("/idempotency/{key}") public Payment byKey(@PathVariable String key,@AuthenticationPrincipal Jwt principal){return payments.findByClientIdAndIdempotencyKey(principal.getSubject(),key).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));}

    public record PaymentPage(List<Payment> content, int page, int size, long totalElements,
                              int totalPages, boolean first, boolean last) {}
}
