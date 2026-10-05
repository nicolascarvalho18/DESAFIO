# Payments Idempotency API

API Spring Boot para o desafio de idempotência em pagamentos. O projeto não possui frontend e não depende de Docker ou Docker Compose. A demonstração e os testes são feitos diretamente pela API e pelo Swagger.
## Arquitetura

API REST em **Java 21 e Spring Boot**, organizada em camadas (**Controller, Service e Repository**) para separar responsabilidades e facilitar manutenção. Utiliza **Spring Security/JWT**, **JPA**, **H2** no desenvolvimento e **PostgreSQL + RabbitMQ** no perfil de produção. A idempotência é controlada por `Idempotency-Key`, hash **SHA-256** e restrição única no banco; o **Transactional Outbox** e a deduplicação de eventos permitem recuperar falhas sem criar novas intenções de pagamento. Inclui **Swagger/OpenAPI**, **Actuator** e testes automatizados.

