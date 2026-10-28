# ADR 0001: processamento assíncrono durável

## Decisão

O POST persiste a solicitação em `PENDING` e retorna `201` sem aguardar o provedor. A própria linha é o job durável. Um worker seleciona lotes elegíveis com `FOR UPDATE SKIP LOCKED`, muda-os para `PROCESSING` e confirma a transação antes de chamar o provedor.

## Motivos

O provedor pode levar segundos ou ficar indisponível. Separar criação e ativação mantém baixa a latência do POST, não mantém transação aberta durante I/O e permite retomar trabalho após reinício. A solução evita um broker desnecessário para o tamanho do desafio.

## Semântica e concorrência

A entrega é pelo menos uma vez, não exatamente uma vez. Timeout é ambíguo: o provedor pode ter ativado antes da perda da resposta. `externalId` é enviado como `Idempotency-Key` para tornar repetição segura no contrato definido para o mock. O `traceId` de criação é persistido como correlação, restaurado no MDC do worker e enviado em `X-Correlation-ID`, ligando a requisição ao processamento mesmo quando o worker possui outro trace. Claims concorrentes usam locks com `SKIP LOCKED`; `@Version` protege alterações otimistas; `PROCESSING` antigo volta a `PENDING` ou termina em `FAILED` se esgotado.

## Consequências

O cliente consulta o estado. Em 429/503, um `Retry-After` válido define a próxima tentativa; as demais falhas temporárias usam backoff exponencial com jitter persistido. O máximo é de quatro tentativas. Um circuit breaker distribuído não foi adicionado: em produção ele seria configurado no cliente ou service mesh após observar volume e orçamento de erro, sem substituir retry durável.
