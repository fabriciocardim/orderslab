# Research: Teste de Integração HTTP Baseline

## Decisão 1: `RestTestClient` em vez de `TestRestTemplate`

**Decision**: usar `org.springframework.test.web.servlet.client.RestTestClient` (Spring
Framework 7), habilitado via `@AutoConfigureRestTestClient`
(`org.springframework.boot.resttestclient.autoconfigure`), injetado com
`@Autowired RestTestClient restTestClient` — sem precisar de `@LocalServerPort`.

**Rationale**: confirmado via `javap`/inspeção de bytecode (antes desta spec) que:
- `TestRestTemplate` ainda existe, mas moveu de pacote pra
  `org.springframework.boot.resttestclient.TestRestTemplate` (Boot 4.1.1) — mais uma
  reorganização de pacote, não uma remoção.
- `RestTestClient` é a ferramenta nova (API fluente, mesmo estilo do `WebTestClient`:
  `.get()/.post().uri(...).exchange().expectStatus()...expectBody().jsonPath(...)`).
- A auto-configuração (`RestTestClientTestAutoConfiguration`) detecta automaticamente o
  servidor local rodando (`LocalTestWebServer.get(applicationContext)`) quando
  `@SpringBootTest(webEnvironment = RANDOM_PORT)` está em uso, e configura o `RestTestClient`
  para apontar pra essa porta sozinho — dispensa injetar `@LocalServerPort` e montar a URL
  manualmente, como seria necessário com `TestRestTemplate`.

**Alternatives considered**: `TestRestTemplate` (clássico): funcionaria, mas é o padrão
sendo suplantado pelo `RestTestClient` nesta versão do Spring; como o Princípio II da
constitution já pede "fluxo técnico real" e o laboratório está adotando as ferramentas
atuais do Boot 4.1.1 em vez de replicar convenções do 3.x (ver decisões similares nos itens
1.1, 1.4, 1.7), `RestTestClient` é a escolha consistente.

## Decisão 2: Como enviar o corpo da requisição

**Decision**: mesmo padrão do item 1.7 — corpo como `String` JSON literal via
`.contentType(MediaType.APPLICATION_JSON).body("{...}")`, sem `ObjectMapper` (que no Boot
4.1.1 é `tools.jackson.databind.ObjectMapper`, Jackson 3 — decisão já registrada no item
1.7 de não depender desse pacote).

**Rationale**: consistência com a feature anterior; evita reintroduzir a mesma incerteza de
pacote já contornada.

## Decisão 3: Estrutura do teste — substituir, não adicionar

**Decision**: o arquivo `*ApplicationTests.java` existente (com o `contextLoads()` vazio) é
**reescrito**, não duplicado — passa a conter o teste de fluxo real, com
`@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` +
`@AutoConfigureRestTestClient` na própria classe.

Fluxo coberto (idêntico em forma nos 3 serviços, valores/ações específicos por serviço):

1. `POST` de criação → capturar o `id` do corpo da resposta (`201`).
2. `POST {id}/confirm` (ou `/issue`) → `200`, novo status.
3. `GET /{id}` → `200`, confirma que o status mudou (persistiu entre requisições).
4. Um segundo recurso criado à parte → `POST {id}/cancel` → `200`, confirma a segunda ação.

**Rationale**: `*ApplicationTests.java` já é o lugar convencional (Spring Initializr) para o
teste de integração "the app actually works" de um projeto Spring Boot — substituir seu
conteúdo é mais direto do que criar um arquivo paralelo com o mesmo propósito.

**Alternatives considered**: criar uma classe nova (`OrderApiIntegrationTest.java`) mantendo
o `contextLoads()` como está: rejeitado — duplicaria o propósito ("o app sobe") sem
agregar valor, e deixaria um teste vazio ao lado de um teste real fazendo a mesma verificação
implícita (o contexto sobe) como pré-requisito.

## Conclusão

3 arquivos `*ApplicationTests.java` reescritos (um por serviço), cada um com um teste de
fluxo real via `RestTestClient`. Nenhuma dependência nova (tudo já vem de
`spring-boot-starter-webmvc-test`, item 1.1). Nenhuma mudança em código de produção.
