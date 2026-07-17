# Architecture Notes (MVC + Camadas)

## Objetivo
Reduzir acoplamento da UI e preparar manutencao evolutiva.

## Camadas
- `model/`: entidades de dominio (records)
- `infra/`: acesso de baixo nivel (SQLite, conexao e migracoes)
- `repository/`: consultas SQL por agregado
- `service/`: regras de negocio e consolidacoes
- `app/`: composition root (wiring de dependencias)
- `ui/`: controladores e views JavaFX (em migracao)

## Funcionalidades implementadas

### Recorrencia de tarefas (Palm Desktop / Outlook / MS-Project)

#### Modelo
- `ScheduleType` enum: `SINGLE` | `RANGE` | `WEEKLY`
- `Task` record: campos `scheduleType`, `endDate`, `recurrenceDays`
  - `recurrenceDays`: dias da semana no formato strftime('%w') do SQLite
    - `0`=Dom, `1`=Seg, `2`=Ter, `3`=Qua, `4`=Qui, `5`=Sex, `6`=Sab
    - Exemplo: `"1,3,5"` = Seg, Qua, Sex

#### Schema (migrations em `Database.runMigrations()`)
```sql
ALTER TABLE tasks ADD COLUMN schedule_type TEXT NOT NULL DEFAULT 'SINGLE';
ALTER TABLE tasks ADD COLUMN end_date TEXT;
ALTER TABLE tasks ADD COLUMN recurrence_days TEXT;
```

#### Logica de consulta (`TaskRepository`)
- `SINGLE`: `due_date = ?`
- `RANGE`:  `due_date <= ? AND end_date >= ?`
- `WEEKLY`: `due_date <= ? AND end_date >= ? AND instr(...recurrence_days...) > 0`

#### Validacao (`TaskService.createTask()`)
- RANGE/WEEKLY exigem `endDate`
- `endDate` deve ser posterior a `startDate`
- WEEKLY exige pelo menos um dia marcado

#### UI (`HelloApplication.buildAgendaSidePanel()`)
- ComboBox: "Dia unico" / "Intervalo de datas" / "Dias da semana"
- DatePicker de termino (aparece para RANGE e WEEKLY)
- Checkboxes de dias da semana Dom–Sab (aparece para WEEKLY)
- Visibilidade condicional via `setVisible + setManaged`
- Criacao via `AppContextHolder.get().taskService().createTask()`
- Conclusao via `AppContextHolder.get().taskService().markDone()`

### Camada de Inteligencia (aba dedicada)
- `NaturalLanguageTaskParser`: captura rapida em PT (data/hora/prioridade/categoria) — puro
- `TaskPrioritizer`: score de urgencia (Eisenhower + prazo + atraso + momentum) e foco do dia — puro
- `InsightsEngine`: metricas de vazao/streak/recomendacoes — puro
- `InsightsService`: fachada que combina `TaskRepository` com o nucleo puro
- Migracao `tasks.completed_at` habilita analytics reais de conclusao
- Primeira suite de testes (JUnit 5) cobrindo parser, priorizador, engine e integracao com SQLite

## Estado atual
- Estrutura de pacotes: model, infra, repository, service, app criados e compilando
- `Database` centraliza conexao e migracoes (com construtor testavel `Database(String jdbcUrl)`)
- Repositorios por contexto implementados
- `TaskService`, `AlertService`, `DashboardService` e `InsightsService` adicionados
- `AppContext` centraliza o wiring; `Launcher` inicializa antes da UI
- **Criacao e conclusao de tarefas migradas para `TaskService`**
- Leitura/listagem ainda via `DatabaseService` legado (migracao pendente)

## Proxima fase
1. Migrar leituras da agenda para `TaskService` / `TaskRepository`
2. Extrair controllers por aba (`AgendaController`, `FinanceController`, etc.)
3. Mover layout para classes de view/fxml por modulo
4. Remover gradualmente `DatabaseService` legado apos paridade completa

## Convencoes
- Repositorio: sem regra de negocio
- Service: sem dependencia de JavaFX
- UI: sem SQL direto
- Mensagens de erro/validacao concentradas no service quando possivel

