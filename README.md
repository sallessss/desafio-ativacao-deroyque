# API de ativação de produtos

API REST em Java 21 e Spring Boot 3.5.16 para registrar solicitações e ativá-las de forma assíncrona por um provedor externo. PostgreSQL é a fonte de verdade; Flyway gerencia o schema.

## Executar

Pré-requisitos: JDK 21, Docker e Docker Compose. No Windows, `mvnw.cmd`; em Unix, `./mvnw`.

```bash
./mvnw test
./mvnw -Pintegracao verify
docker compose up --build
```

O Compose inicia PostgreSQL, aplicação, WireMock e um servidor OAuth 2.0 simulado. O mock de autenticação existe somente para desenvolvimento e CI; ele não substitui um provedor de identidade real em produção. Variáveis principais: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_JWK_SET_URI`, `JWT_ISSUER`, `JWT_AUDIENCE`, `PROVIDER_BASE_URL`, `PROVIDER_TOKEN`, `PROCESSING_BATCH_SIZE` e `STATUS_METRICS_INTERVAL`. Segredos não têm valor real no repositório.

## Contrato HTTP

OAuth 2.0 Client Credentials é o fluxo indicado para clientes máquina a máquina. O JWT deve ter assinatura válida, `iss`, `aud`, expiração, `sub` estável do consumidor e escopo `requests.write` ou `requests.read`. A API é stateless e não cria sessão HTTP.

```http
POST /api/v1/requests
Authorization: Bearer <jwt com requests.write>
Content-Type: application/json

{"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}
```

Retorna `201 Created`, `Location: /api/v1/requests/{id}` e os campos `id`, `externalId`, `customerId`, `productCode`, `status`, `createdAt` e `updatedAt`. O estado inicial é `PENDING`.

`GET /api/v1/requests/{id}` exige `requests.read`. O `sub` do JWT é salvo como proprietário; consultar UUID de outro consumidor responde 404, evitando confirmar sua existência.

`GET /api/v1/requests?status=&customerId=&page=&size=` aceita filtros opcionais. `page` inicia em 0 (padrão 0), `size` padrão 20 e máximo 100. A ordem estável é `createdAt DESC, id DESC`. Resposta: `content`, `page`, `size`, `totalElements` e `totalPages`.

Campos de entrada aceitam de 1 a 64 caracteres, somente letras ASCII, números, ponto, `_` e `-`. IDs de consumidor (`sub`) têm até 128 no banco. Datas são `Instant` em UTC/ISO-8601.

Erros usam `application/problem+json` com `type`, `title`, `status`, `detail`, `instance` e `traceId`; validação inclui `errors[{field,message}]`. O campo `type` usa URNs neutras no formato `urn:desafio-ativacao:problem:<tipo>`, sem depender de domínio externo. Códigos: 400 entrada inválida, 401 JWT ausente/inválido, 403 escopo ausente, 404 não encontrado, 409 `externalId` duplicado e 500 erro inesperado genérico.

## Teste manual pelo Swagger UI

Não é necessário instalar ou abrir o Postman. Inicie o ambiente e aguarde os health checks:

```bash
docker compose up --build --wait
```

Abra `http://localhost:8080/swagger-ui.html`. A documentação apresenta os três endpoints da API, parâmetros, schemas, respostas HTTP e escopos necessários. Para executar uma chamada:

1. Clique em `Authorize`.
2. Informe `client_id` como `consumidor-a` e `client_secret` como `segredo-local`.
3. Selecione `requests.read` e `requests.write` e confirme em `Authorize`.
4. Abra um endpoint, clique em `Try it out`, preencha os dados e clique em `Execute`.

O Swagger obtém o JWT em `http://localhost:8081/default/token` e o envia como Bearer Token. Para testar isolamento BOLA, autorize novamente usando `consumidor-b` e tente consultar um UUID criado por `consumidor-a`; a resposta esperada é `404 Not Found`.

A especificação OpenAPI em JSON está disponível em `http://localhost:8080/v3/api-docs`. Em produção, defina `SWAGGER_ENABLED=false` caso a documentação não deva ser exposta, ou restrinja essas rotas no gateway.

## Teste manual no Postman

Inicie o ambiente e aguarde os health checks:

```bash
docker compose up --build --wait
```

No Postman, crie primeiro a requisição `Obter token`:

- Método: `POST`
- URL: `http://localhost:8081/default/token`
- Body: `x-www-form-urlencoded`
- `grant_type`: `client_credentials`
- `client_id`: `consumidor-a`
- `client_secret`: `segredo-local`
- `scope`: `requests.read requests.write`

O `client_secret` acima é apenas um valor descartável aceito pelo mock local, não uma credencial real. Copie o campo `access_token` da resposta para uma variável de ambiente do Postman chamada `accessToken`. Nas requisições da API, escolha `Authorization` > `Bearer Token` e informe `{{accessToken}}`.

Alternativamente, na aba `Authorization` do Postman, selecione `OAuth 2.0` e configure `Grant Type: Client Credentials`, `Access Token URL: http://localhost:8081/default/token`, `Client ID: consumidor-a`, `Client Secret: segredo-local`, escopo `requests.read requests.write` e `Client Authentication: Send as Basic Auth header`. Clique em `Get New Access Token` e depois em `Use Token`.

Com o token, execute:

```http
POST http://localhost:8080/api/v1/requests
Authorization: Bearer {{accessToken}}
Content-Type: application/json

{
  "externalId": "EXT-POSTMAN-001",
  "customerId": "CUST-10001",
  "productCode": "INTERNET-500"
}
```

A criação válida retorna `201 Created`. Guarde o `id` retornado para consultar `GET http://localhost:8080/api/v1/requests/{id}`. A listagem é `GET http://localhost:8080/api/v1/requests?page=0&size=20`; os filtros opcionais são `status` e `customerId`.

Casos úteis para conferência manual:

- Repetir o mesmo `externalId`: `409 Conflict`.
- Remover `customerId` ou enviar formato inválido: `400 Bad Request` com `errors`.
- Consultar um UUID inexistente: `404 Not Found`.
- Omitir o token: `401 Unauthorized`.
- Gerar token apenas com `requests.read` e tentar o POST: `403 Forbidden`.
- Gerar token com `client_id=consumidor-b` e consultar o UUID criado por `consumidor-a`: `404 Not Found`, sem revelar o recurso de outro consumidor.

O `client_id` do mock é usado como claim `sub`, a audiência é `desafio-ativacao` e o emissor é `http://localhost:8081/default`. A aplicação continua validando assinatura, emissor, audiência, expiração e escopos pelo JWKS. O servidor OAuth simulado é deliberadamente inseguro como autoridade de autenticação e nunca deve ser exposto ou usado em produção.

## Unicidade e idempotência

`external_id` possui constraint única real. A API não transforma repetição da origem em sucesso: qualquer `externalId` existente retorna 409, inclusive payload idêntico; payload diferente também é conflito. Isso evita que credenciais de outro consumidor descubram ou reutilizem dados. Criações simultâneas são arbitradas pelo PostgreSQL. Para idempotência HTTP completa em produção, uma tabela de chaves com hash do payload poderia devolver a resposta original apenas ao mesmo consumidor.

## Provedor e estados

O contrato do mock foi definido por esta solução, não pelo enunciado: `POST /provider/v1/activate` recebe os três campos do pedido, os headers `Idempotency-Key: <externalId>` e `X-Correlation-ID: <correlationId>`, e retorna `{"status":"ACTIVATED"}`.

- `PENDING -> PROCESSING -> COMPLETED`: HTTP 2xx com `ACTIVATED`.
- `PENDING -> PROCESSING -> FAILED`: 4xx permanente ou corpo não reconhecido.
- Timeout, falha de conexão, 408, 429 e 5xx: `PROCESSING -> PENDING`, com nova data persistida; 429/503 respeitam `Retry-After` em segundos ou data HTTP quando válido, e as demais respostas usam backoff exponencial com jitter. Após quatro tentativas, o estado final é `FAILED`.
- `PROCESSING` por mais de dois minutos é reconciliado. Como timeout pode esconder ativação concluída, o provedor deve honrar a chave idempotente.

Conexão e leitura usam, por padrão, 2s e 5s. O worker faz claim transacional com `FOR UPDATE SKIP LOCKED`; a chamada remota ocorre após commit. A semântica real é pelo menos uma vez. Veja [ADR 0001](docs/adr/0001-processamento-assincrono-duravel.md).

## Persistência e índices

Flyway cria constraints coerentes com os DTOs. Índices `(consumer_id,status,created_at,id)` e `(consumer_id,customer_id,created_at,id)` atendem filtros e ordenação; índices parciais de `PENDING` e `PROCESSING` atendem claim e reconciliação. `@Version` protege atualização concorrente.

## Testes

Testes unitários cobrem estados, retry, `Retry-After`, timeout, correlação e mapeamento do provedor; MockMvc cobre contrato de criação e consulta existente, validação, paginação, 201/Location, 400, 404, 409, 500, 401 e 403. O perfil `integracao` usa Testcontainers/PostgreSQL para migrations, constraint concorrente, filtros, isolamento BOLA, claim concorrente com `SKIP LOCKED` e lock otimista. Outro teste integrado executa o worker agendado contra PostgreSQL e WireMock e usa Awaitility para verificar sucesso, falha permanente, retry temporário, `Retry-After`, timeout e propagação de correlação, sem sleeps frágeis. Nenhum teste usa serviços ou credenciais pessoais. O Docker daemon precisa estar disponível para o perfil de integração.

## Observabilidade e operação

Actuator publica liveness/readiness e Prometheus. A latência e a taxa de erros da API usam a métrica padrão `http.server.requests`; HTTP recebe tracing Micrometer/OpenTelemetry. Na criação, o `traceId` da requisição é persistido como `correlationId`; o worker o restaura no MDC e o propaga ao provedor em `X-Correlation-ID`. O worker agendado pode iniciar um novo trace, mas a correlação persistida liga as duas partes do fluxo assíncrono. Chamadas do provedor geram `provider.activation.duration` e `provider.activation.requests`, com label de baixa cardinalidade. A contagem `activation.requests` usa um snapshot periódico obtido por uma única consulta agrupada, evitando uma consulta ao banco para cada status e para cada coleta do Prometheus. Logs de console são JSON Logstash e incluem contexto de tracing automaticamente; payloads, tokens e segredos não são logados. OTLP aponta para endpoint configurável. Health público não mostra detalhes; Prometheus deve ser restrito pela rede/gateway.

O limite de `size` e cabeçalhos está na aplicação. Tamanho máximo de corpo e rate limit devem ser aplicados no gateway/reverse proxy (por exemplo 16 KiB e quota por client ID), porque Spring MVC não oferece rate limit distribuído. Produção deve usar secret manager, rotação do token do provedor, TLS, pool dimensionado, migrations como etapa de deploy e shutdown gracioso.

## Diagnóstico de lentidão

1. Começar por traces ponta a ponta e comparar p50/p95/p99 por API, SQL e provedor no mesmo intervalo.
2. Separar tempo em fila/pool/thread da aplicação, aquisição/execução de conexão e span HTTP externo; assim se evita atribuir 2,5s apenas à CPU de 45%.
3. Observar erros/timeouts, pool Hikari, threads, GC, locks, slow queries, planos, linhas lidas, latência/erros do provedor e saturação de rede/DNS.
4. Melhorar somente após evidência: índice/plano/query, pool e timeout, controle de concorrência, cache apropriado ou negociação/SLA do provedor.
5. Uma causa comum é possível (rede, DNS, banco/provedor compartilhando infraestrutura, explosão de tráfego), mas correlação temporal não prova causalidade; confirmar por traces, métricas de dependência e eventos de infraestrutura.
6. Publicar progressivamente e comparar a mesma carga/janela antes/depois, acompanhando percentis, throughput e taxa de erro; usar rollback se health ou orçamento de erro piorar.

## Cloud e CI/CD

Múltiplas instâncias compartilham PostgreSQL: constraint única, `SKIP LOCKED` e versão impedem processamento incompatível. Configuração vem de ambiente e segredos de um secret manager. Se outros serviços precisarem do evento de conclusão, uma outbox separada publicará em tópico/fila com entrega pelo menos uma vez e consumidor idempotente. Pipeline: compilar, testes unitários/integrados, análise de segurança, imagem imutável, push, migration, deploy gradual, readiness e rollback automático.

O repositório contém duas esteiras GitHub Actions, executadas em pull requests, em pushes para `main` e manualmente:

- `Testes`: configura Java 21, usa o Maven Wrapper, executa `./mvnw -B -ntp -Pintegracao verify` e publica relatórios Surefire/Failsafe por sete dias.
- `Build Docker Compose`: valida o Compose, constrói a imagem multi-stage, sobe aplicação, PostgreSQL, WireMock e o servidor OAuth simulado, aguarda os health checks, obtém um JWT por Client Credentials, valida criação e consulta autenticadas e remove o ambiente mesmo em caso de falha.

As esteiras não publicam imagens nem fazem deploy porque o desafio não define registry ou ambiente de destino. Para entrega contínua real, o job de `main` autenticaria no registry por OIDC ou segredo de curta duração, publicaria a imagem pelo digest e acionaria o deploy gradual no ambiente configurado, com migrations, readiness e rollback. Nenhum segredo de CI é necessário para as verificações atuais.

## Limitações conscientes

Não foi criada infraestrutura cloud/Kubernetes/Terraform. Rate limit e limite estrito do corpo ficam no gateway. Circuit breaker não foi incluído porque retry durável e backoff já protegem este fluxo pequeno; deve ser avaliado com métricas em produção. O Compose fornece somente um mock OAuth para testes locais; um IdP real continua sendo responsabilidade do ambiente de produção.

## Uso de IA

IA generativa auxiliou na estrutura inicial, implementação, criação de testes e documentação. As decisões foram confrontadas com o enunciado, e os comandos/testes relatados correspondem a execuções reais; revisão humana continua necessária antes de produção.
