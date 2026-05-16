# Changelog

Todas as mudanças relevantes deste projeto são documentadas neste arquivo.

O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o
projeto adota [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [1.1.0] - 2026-05-16

### Adicionado
- Coluna **Observação** na tabela principal com edição rápida inline (textarea
  por linha), permitindo registrar um resumo breve do dia junto com os horários.
- Salvamento em lote: qualquer botão **Salvar ✓** envia todas as linhas
  modificadas via novo endpoint `POST /day/bulk_update`. Linhas alteradas
  ficam destacadas visualmente até a confirmação.
- Servidor MCP (`fastmcp`) montado em `/mcp` expondo as operações de
  cadastro/consulta de dias e períodos como ferramentas MCP.
- Integração OpenAPI via `flask-openapi3`, com endpoints REST para o app.
- Workflow GitHub Actions (`docker-publish.yml`) para build e publicação da
  imagem no GHCR a cada release.
- Healthcheck no `docker-compose.yml` e instruções de deploy no README.

### Alterado
- Transport MCP migrado de **SSE** para **streamable-http**; o endpoint
  canônico passa a ser `POST /mcp` (sem barra final). Requisições para `/mcp/`
  recebem 307 para `/mcp`.
- Edição rápida (`POST /day/<data>/quick_update`) agora aceita atualização
  apenas de observação em dias com mais de 2 períodos, em vez de bloquear o
  salvamento.
- Aviso de segurança no README reforçado, recomendando autenticação HTTP
  na frente do app.

### Corrigido
- Serialização de `datetime` nos schemas Pydantic (`model_dump(mode='json')`).

## [1.0] - 2026-05-02

Primeira release pública.
