# Guia de Desenvolvimento — Agenda Pessoal

> Documentação técnica das partes mecânicas do projeto.  
> Atualizar este arquivo junto com qualquer mudança estrutural.

---

## Estrutura de Pacotes

```
com.pessoal.agenda/
├── AgendaApp.java          — Entry point JavaFX, wiring de controllers e abas
├── Launcher.java           — Shim para JPMS (chama AgendaApp.main)
├── DatabaseService.java    — Serviço legado de consultas (migração em andamento)
│
├── app/
│   ├── AppContext.java         — Composition root: instancia e conecta todos os repositórios/serviços
│   ├── AppContextHolder.java   — Singleton de acesso global ao AppContext
│   └── SharedContext.java      — Estado reativo compartilhado entre todos os controllers de UI
│                                 (ObservableLists, Labels de KPI, callbacks de refresh)
│
├── infra/
│   └── Database.java           — Conexão SQLite + runMigrations() (criação/evolução de schema)
│
├── model/                      — Records imutáveis de domínio (sem lógica de negócio)
│   ├── Task.java
│   ├── TaskPriority.java / TaskStatus.java / ScheduleType.java
│   ├── Protocol.java / ProtocolExecutionType.java
│   ├── FinanceEntry.java
│   ├── ProjectIdea.java / IdeaChecklistItem.java
│   ├── ChecklistItem.java
│   ├── InventoryItem.java
│   ├── AttendanceDay.java / MonthSummary.java
│   ├── Category.java / CategoryDomain.java
│   └── TaskSession.java
│
├── repository/                 — Apenas SQL, sem regras de negócio
│   ├── TaskRepository.java
│   ├── TaskSessionRepository.java
│   ├── ProtocolRepository.java
│   ├── StudyEntryRepository.java
│   └── ...
│
├── service/                    — Regras de negócio, sem dependência de JavaFX
│   ├── TaskService.java
│   ├── CategoryService.java
│   ├── TaskTimerService.java       — Singleton de timer de sessão ativa
│   ├── PendencyNotificationService.java — Alertas periódicos de pendências (TDAH)
│   ├── NaturalLanguageTaskParser.java — Captura rápida em PT → campos estruturados (puro)
│   ├── TaskPrioritizer.java        — Score de urgência + foco do dia (puro)
│   ├── InsightsEngine.java         — Métricas/streak/recomendações (puro)
│   ├── InsightsService.java        — Fachada de inteligência (usa TaskRepository)
│   ├── GoogleAuthService.java / GoogleTasksService.java
│   └── ...
│
├── tools/
│   └── ICalendarExporter.java  — Exportação de tarefas para .ics
│
└── ui/
    ├── controller/
    │   ├── AgendaTabController.java    — Aba "Agenda e Prioridades"
    │   ├── DashboardController.java    — Aba "Dashboard" com KPIs e alertas TDAH
    │   ├── ChecklistController.java    — Aba "Protocolos Operacionais"
    │   ├── StudyController.java        — Aba "Estudos e Atividades"
    │   ├── FinanceController.java      — Aba "Financeiro e Pendências"
    │   ├── IdeasController.java        — Aba "Banco de Ideias"
    │   ├── SalesController.java        — Aba "Vendas"
    │   ├── ConfigController.java       — Aba "Configurações"
    │   ├── InsightsController.java     — Aba "🧠 Inteligência" (captura + analytics)
    │   └── UIHelper.java               — Factories de componentes reutilizáveis
    ├── view/                           — Janelas secundárias (Stage independentes)
    │   ├── Dialogs.java                — Factory central de Alert/Dialog (usa Modality.NONE)
    │   ├── ThemeManager.java           — Aplica/troca CSS de tema em tempo real
    │   ├── ProtocolExecutionWindow.java
    │   ├── TaskTimerWindow.java
    │   ├── SessionHistoryWindow.java
    │   └── ...
    └── util/
        └── PrintReportService.java
```

---

## Sistema de Temas (CSS)

### Arquivos

| Arquivo | Papel |
|---|---|
| `app.css` | Regras base + tokens do tema claro (variáveis `-t-*`) |
| `theme-dark.css` | Apenas redefine os tokens `-t-*` + overrides de controles nativos |
| `timer-inline.css` | Estilo do timer inline nas células de lista |

### Convenção de Tokens

Todos os tokens seguem o padrão `-t-<contexto>`. **Nunca usar cores hardcoded (`#hex`) em regras CSS**; sempre usar tokens.

| Token | Uso |
|---|---|
| `-t-app-bg` | Fundo geral da janela |
| `-t-surface` | Fundo de cards e painéis |
| `-t-surface-a/b/c/d` | Variantes sutis de surface |
| `-t-text` | Texto principal |
| `-t-text-m` / `-t-text-m2` | Texto secundário / hint |
| `-t-text-inv` | Texto sobre fundos coloridos (botões primários) |
| `-t-pri` / `-t-pri-dk` | Cor primária / escurecida |
| `-t-bd` / `-t-bd-lt` | Bordas principais / sutis |
| `-t-selected` / `-t-selected2` | Seleção ativa / sem foco |
| `-t-hover` | Hover de células |
| `-t-err` / `-t-err-bg` | Erro/perigo |
| `-t-cal-*` | Calendário de frequência |
| `-t-inp-bg` / `-t-inp-bd` | Inputs (TextField, DatePicker) |

### Regra de Seleção em Listas/Tabelas

Para garantir visibilidade em linhas alternadas, sempre cobrir os 3 estados:

```css
.minha-lista .list-cell:filled:selected,
.minha-lista .list-cell:filled:selected:odd,
.minha-lista .list-cell:filled:selected:even {
    -fx-background-color: -t-selected2; /* sem foco */
}
.minha-lista:focused .list-cell:filled:selected,
.minha-lista:focused .list-cell:filled:selected:odd,
.minha-lista:focused .list-cell:filled:selected:even {
    -fx-background-color: -t-selected; /* com foco */
}
```

---

## ThemeManager

`com.pessoal.agenda.ui.view.ThemeManager`

- Singleton: `ThemeManager.getInstance()`
- `applyTo(Scene scene)` — registra a cena e aplica tema atual
- `setTheme(Theme theme)` — troca o tema em **todas as cenas abertas** simultaneamente
- Temas disponíveis: `Theme.LIGHT` (padrão), `Theme.DARK`
- Hook global: `initGlobalWindowHook()` intercepta janelas novas e aplica tema automaticamente

---

## SharedContext — Estado Reativo

`com.pessoal.agenda.app.SharedContext`

Centraliza todo o estado que é compartilhado entre os controllers. Controllers nunca conversam entre si diretamente.

| Campo | Tipo | Uso |
|---|---|---|
| `openTasksValue` | `Label` | KPI de tarefas abertas no dashboard |
| `overdueTasksValue` | `Label` | KPI de tarefas atrasadas |
| `alertItems` | `ObservableList<String>` | Alertas de atraso (dashboard) |
| `upcomingItems` | `ObservableList<String>` | Próximos prazos (dashboard) |
| `todayTaskItems` | `ObservableList<String>` | **TDAH**: tarefas de hoje |
| `expiringProtocolItems` | `ObservableList<String>` | **TDAH**: protocolos periódicos |
| `tasksDueCountLabel` | `Label` | Contagem KPI de hoje |
| `protocolsExpiringCountLabel` | `Label` | Contagem KPI de protocolos |
| `taskCatNames` | `ObservableList<String>` | Categorias de tarefa (compartilhado entre Agenda e Config) |

### Callbacks de Refresh

```java
ctx.setDashboardRefreshCallback(() -> dashboardCtrl.refreshKpis(YearMonth.now()));
ctx.setAlertRefreshCallback(() -> { ... });
ctx.triggerDashboardRefresh(); // chama o callback registrado
```

---

## AppContext — Wiring de Dependências

`com.pessoal.agenda.app.AppContext`

Ponto central de construção de objetos. Controllers recebem dependências via construtor — nunca instanciam repositórios diretamente.

```java
AppContextHolder.get().taskService()             // TaskService
AppContextHolder.get().taskRepository()          // TaskRepository
AppContextHolder.get().taskSessionRepository()   // TaskSessionRepository
AppContextHolder.get().protocolRepository()      // ProtocolRepository
AppContextHolder.get().studyEntryRepository()    // StudyEntryRepository
AppContextHolder.get().categoryService()         // CategoryService
```

---

## Database — Migrações

`com.pessoal.agenda.infra.Database`

- `runMigrations()` executa todas as migrações na inicialização
- Migrações são **idempotentes** (`CREATE TABLE IF NOT EXISTS`, `ALTER TABLE` com try/catch)
- Nunca remover migrações existentes; sempre adicionar novas ao final

---

## PendencyNotificationService — Alertas TDAH

`com.pessoal.agenda.service.PendencyNotificationService`

Singleton que verifica pendências periodicamente:

- Tenta tocar `src/main/resources/sounds/reminder.wav`
- Se o arquivo falhar ou não existir, usa beep do sistema como fallback

```java
// Iniciar (geralmente em AgendaApp.start())
PendencyNotificationService.getInstance().start(
    5 * 60 * 1000,   // intervalo: 5 minutos
    () -> {
        refreshAlertsAndUpcoming();
        refreshDashboardKpis();
        updateCriticalBadge();
    }
);

// Sinalizar que há alertas (chamado por DashboardController após refresh)
PendencyNotificationService.getInstance().setHasAlerts(count > 0);

// Parar (ao fechar a aplicação)
PendencyNotificationService.getInstance().stop();
```

### Barra de status — badge crítico

- Label secundário na barra de status: `statusAlertBadge`
- Estados visuais:
  - `status-alert-ok` (sem pendências críticas)
  - `status-alert-critical` (com pendências críticas)
- Em estado crítico, animação de piscar via `Timeline` para chamar atenção

### Atalho de lembrete manual

- Atalho global: `Ctrl+S` (Linux/Windows) / `Cmd+S` (macOS)
- Implementação via `KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN)`
- Ação:
  1. Atualiza alertas e KPIs
  2. Força `PendencyNotificationService.forceCheck()`
  3. Exibe status textual de lembrete manual

---

## Camada de Inteligência (aba 🧠)

`com.pessoal.agenda.service.{NaturalLanguageTaskParser, TaskPrioritizer, InsightsEngine, InsightsService}`
+ `ui.controller.InsightsController`

Arquitetura em duas partes para maximizar testabilidade:

- **Núcleo puro** (sem UI/banco): `NaturalLanguageTaskParser`, `TaskPrioritizer`, `InsightsEngine`.
  Recebem entradas explícitas (`List<Task>`, `Map<LocalDate,Integer>`, `LocalDate today`) e
  retornam dados. São exercitados por testes unitários com uma data-base fixa.
- **Fachada com repositório**: `InsightsService` reúne os dados persistidos (`TaskRepository.findAll()`
  e `completionCountsSince()`) e delega o cálculo ao núcleo puro.

### NaturalLanguageTaskParser

`parse(String, LocalDate today)` → `ParsedTask(title, date, startTime, priority, category)`.
Reconhece expressões de data/hora/prioridade/categoria e devolve o título já limpo dos tokens.

> ⚠️ As regex usam `Pattern.UNICODE_CHARACTER_CLASS` — sem isso, `\b`/`\w` são ASCII e falham
> em fronteiras de palavra com acento (`amanhã`, `às`, `sábado`).

### Score de urgência (`TaskPrioritizer`)

`score = pesoPrioridade + urgênciaPrazo + momentum(EM_ANDAMENTO)`.
`focusOfTheDay()` prefere a tarefa de maior score que vence hoje ou está atrasada.

### completed_at

A migração `ALTER TABLE tasks ADD COLUMN completed_at TEXT` habilita analytics reais.
Todas as rotas de conclusão preenchem a coluna via `COALESCE(completed_at, datetime('now','localtime'))`
(`TaskRepository.markDone` e `DatabaseService.markTaskDone`).

---

## Testes

`src/test/java/com/pessoal/agenda/service/` — JUnit 5 (primeira suíte do projeto).

```bash
./mvnw test
```

- Testes de lógica pura (parser, priorizador, engine) e um teste de integração ponta-a-ponta
  (`InsightsServiceIntegrationTest`) que roda migrações reais sobre um SQLite temporário via
  `new Database("jdbc:sqlite:" + tempFile)`.
- `maven-surefire-plugin` roda com `useModulePath=false` (testes no classpath, evitando o
  module-path do JPMS para o código de teste).

---

## Convenções de Código

| Regra | Detalhes |
|---|---|
| Repositório | Sem regra de negócio; só SQL |
| Service | Sem dependência de JavaFX |
| UI | Sem SQL direto |
| Cores em Java | Usar `setStyle("-fx-text-fill: -t-token;")` em vez de `#hex` |
| Diálogos | Sempre usar `Dialogs.*` (nunca `new Alert()`) para garantir `Modality.NONE` |
| Refresh após salvar | Sempre chamar `refreshCurrentView()` e `ctx.triggerDashboardRefresh()` |


