# Changelog

Todas as mudanças relevantes deste projeto são documentadas neste arquivo.

O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o
projeto adota [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [1.2.1] - 2026-09-08

### Alterado
- **Responsividade mobile.** Abaixo de `768px` a tabela de 8 colunas (970px de
  largura natural, 2,5× a tela de um celular) passa a ser renderizada como **um
  card por dia**, via CSS grid sobre a mesma `<table>` — o `<tr>` vira o card e
  as células viram áreas do grid. Feito só em CSS de propósito: o bulk save, o
  dirty tracking e os tooltips continuam presos a `tr[data-date]` e não foram
  tocados.
- Estatísticas e gráfico foram para um `collapse` fechado por padrão no celular
  (`.collapse.d-md-block`: sempre visível a partir de `md`), então a lista de
  dias sobe para o topo. A rolagem até o primeiro dia caiu de **1,1 tela para
  0,28**.
- Alvos de toque de **44px** nos controles em telas pequenas: os campos abaixo
  do mínimo caíram de **54 de 62 para 2 de 52** (restam o brand e o toggler da
  navbar, defaults do Bootstrap, fora do fluxo de ponto).
- A coluna **Esperado** fica oculta no celular (derivável do Tipo).

### Adicionado
- Barra **"Salvar alterações"** fixa no rodapé no mobile, revelada só quando há
  linha alterada. Ela dispara o botão de linha já existente, sem duplicar a
  lógica de salvamento; os botões por linha ficam ocultos nessa largura.
- Favicon inline (SVG em data URI), eliminando o 404 de `/favicon.ico`.

### Infraestrutura
- O workflow de publicação passa a buildar **`linux/amd64` + `linux/arm64`**
  (QEMU + buildx). Os runners do GitHub são amd64 e a imagem saía amd64-only,
  o que a tornava inutilizável nos hosts aarch64 onde o app roda.

### Removido
- CSS morto `.weekend` / `.holiday` no `base.html` — eram cores de tema claro
  que nenhum template usava (as linhas usam `.table-warning` / `.table-light` /
  `.table-consolidated`, já tratadas pelo `catppuccin.css`).

## [1.2.0] - 2026-05-24

### Adicionado
- Visualização de período **Custom** no painel: 4º botão no `btn-group` que
  revela inputs de data (Início/Fim) e um botão **Aplicar**. As médias de
  chegada/saída, saldo diário e a projeção de 90 dias passam a poder ser
  calculadas sobre um intervalo arbitrário, complementando as bases fixas
  7d/30d/90d.
- Endpoint REST `GET /api/stats/custom?start=YYYY-MM-DD&end=YYYY-MM-DD` e tool
  MCP `get_custom_stats_tool(start_date, end_date)` expondo a mesma lógica.
- A média móvel do gráfico segue o tamanho do intervalo selecionado quando o
  modo Custom está ativo.

### Alterado
- `get_dashboard_stats` (em `app/core.py`) refatorado para reutilizar um
  helper `_window_stats` compartilhado com `get_custom_range_stats`.

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
