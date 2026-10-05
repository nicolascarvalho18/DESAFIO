# Payments Idempotency API

API Spring Boot para o desafio de idempotência em pagamentos. O projeto não possui frontend e não depende de Docker ou Docker Compose. A demonstração e os testes são feitos diretamente pela API e pelo Swagger.
## Diagramas do sistema

- [Modelagem da arquitetura](docs/modelagem-arquitetura.svg)
- [Regras de idempotência e recuperação de falhas](docs/regras-idempotencia.svg)

## Executar no Windows

Pré-requisitos: JDK 21 e Maven 3.9+. Não há `mvnw`; instale Maven localmente e adicione `mvn.cmd` ao `PATH`. O script reconhece também Maven instalado em `%LOCALAPPDATA%\Programs\Apache\Maven\apache-maven-3.9.16`.

```powershell
cd "C:\Users\dev\Downloads\Nova pasta (20)"
.\start-local.ps1
```

A API sobe em `http://localhost:8080`. O script abre o Swagger UI, onde é possível fazer login, autorizar com o JWT e exercitar os endpoints. Para iniciar sem abrir navegador, use `.\start-local.ps1 -NoBrowser`. Encerre com `.\stop-local.ps1`.

- Swagger: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

Conta local de desenvolvimento: `demo@example.com` / `Demo123!`. O JWT de acesso é emitido pelo login. H2 persiste em `backend/data/payments.mv.db`.

## Idempotência e fluxo

O cliente envia `POST /api/v1/payments` com um `Idempotency-Key` estável para a intenção original. Para a mesma conta:

- Mesma chave e mesmo conteúdo: a API devolve a intenção original, sem criar outra.
- Mesma chave e conteúdo diferente: a API retorna `409 Conflict`.
- Chaves diferentes representam intenções diferentes.
- O banco impõe unicidade em `(client_id, idempotency_key)` para arbitrar concorrência; o hash SHA-256 compara o conteúdo.
- Intenção e evento outbox são gravados na mesma transação. O consumer deduplica eventos, permitindo recuperação após falhas de publicação/reentrega.

Não existe promessa de entrega exactly-once: o sistema usa entrega at-least-once e deduplicação persistente. Um gateway de pagamento real ainda precisa receber a mesma chave idempotente, e timeouts após aprovação exigem reconciliação com o provedor.

## API

Rotas de pagamento exigem `Authorization: Bearer <token>`. O login e a documentação Swagger são públicos.

```http
POST /api/v1/auth/login
Content-Type: application/json

{"email":"demo@example.com","password":"Demo123!"}
```

```http
POST /api/v1/payments
Authorization: Bearer <token>
Idempotency-Key: order-12345678
Content-Type: application/json

{"amount":42.50,"currency":"BRL","description":"Pedido 123"}
```

Outras rotas: `GET /api/v1/payments` (histórico paginado), `GET /api/v1/payments/{id}` e `GET /api/v1/payments/idempotency/{key}`. O histórico aceita `page`, `size` (1–100), `status`, `from` e `to` (datas `YYYY-MM-DD`, inclusivas). Por exemplo:

```http
GET /api/v1/payments?page=0&size=20&status=APPROVED&from=2026-10-01&to=2026-10-31
Authorization: Bearer <token>
```

## Perfis

`dev` é o padrão para execução simples. Usa H2 em arquivo e dispatcher local em memória, para demonstração/teste do fluxo. Essas garantias locais **não equivalem** à concorrência distribuída nem à durabilidade de mensageria em produção.

`prod` usa PostgreSQL e RabbitMQ via variáveis de ambiente (`SPRING_DATASOURCE_URL`, credenciais `SPRING_DATASOURCE_*`, `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT` e credenciais `SPRING_RABBITMQ_*`). Execute PostgreSQL e RabbitMQ localmente ou use serviços externos; Docker não faz parte da execução requerida.

## Testes

```powershell
cd backend
mvn.cmd test
```

Os testes automatizados cobrem replay idempotente e conflito de payload. Para prova de concorrência e persistência distribuída, execute testes de integração contra PostgreSQL e RabbitMQ reais; H2 não prova essas garantias.

Na suíte atual, `PaymentIdempotencyIntegrationTest` envia 24 chamadas autenticadas em paralelo para a mesma chave no H2 isolado e verifica um ID de pagamento, um evento outbox, conflito 409 para payload alterado e listagem paginada. Execução adicional nesta estação: 40 chamadas HTTP simultâneas retornaram 202 com um único ID; replay retornou o mesmo ID, payload alterado retornou 409, consulta por chave/histórico retornou 200 e Swagger/health retornaram 200/UP. Esses resultados são do ambiente H2 de desenvolvimento.


