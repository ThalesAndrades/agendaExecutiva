# Changelog — Agenda Pessoal

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).  
Versionamento segue [Semantic Versioning](https://semver.org/lang/pt-BR/).

---

## [Não lançado]

### Adicionado
- **🧠 Nova aba "Inteligência"** — camada analítica e de captura inteligente da agenda:
  - `Captura inteligente`: campo em linguagem natural que interpreta automaticamente **data** (hoje, amanhã, depois de amanhã, dia da semana, `dia N`, `DD/MM`, `DD/MM/AAAA`, `em N dias`, próxima semana), **horário** (`14h`, `15:30`, `15h30`, `às 9`, meio-dia, meia-noite), **prioridade** (`!alta`, `!!`, `!!!`, `urgente`, `importante`, `quando puder`) e **categoria** (`#tag`), com pré-visualização em tempo real enquanto digita
  - `Indicadores de produtividade`: taxa de vazão (7 dias), sequência de dias concluindo tarefas (streak), concluídas na semana/mês, abertas e atrasadas
  - `🎯 1 tarefa principal do dia` (foco sugerido) com atalho para abrir direto na Agenda
  - `🔝 Prioridades inteligentes`: tarefas abertas pontuadas por urgência (Eisenhower + proximidade de prazo + persistência do atraso + momentum), com duplo clique para abrir na Agenda
  - `💡 Recomendações` acionáveis baseadas no estado atual
- `NaturalLanguageTaskParser`: interpretador determinístico de captura rápida em português (serviço puro, sem UI/banco)
- `TaskPrioritizer`: motor de priorização com score de urgência e sugestão de "foco do dia"
- `InsightsEngine` (puro) + `InsightsService` (com repositório): consolidação de métricas, streak e recomendações
- `tasks.completed_at`: nova coluna que registra o momento da conclusão, habilitando analytics reais de vazão e sequência (preenchida em todas as rotas de conclusão)
- `Database(String jdbcUrl)`: construtor com URL explícita para testes de integração sobre banco temporário
- **Primeira suíte de testes automatizados do projeto** (JUnit 5, 29 testes): parser de linguagem natural, priorizador, motor de insights e integração ponta-a-ponta com SQLite temporário; `maven-surefire-plugin` configurado
- `SharedContext.triggerTasksChanged()`: ressincroniza todas as abas após a captura inteligente; a aba Inteligência também recalcula automaticamente sempre que é aberta, garantindo dados atualizados independentemente da rota que alterou a tarefa
- `PendencyNotificationService`: serviço background que verifica pendências a cada 5 minutos e toca `sounds/reminder.wav` (com fallback para beep)
- Dashboard: cards "📋 Tarefas de HOJE" e "⚠️ Protocolos Vencendo" com destaque visual para apoio a TDAH
- `SharedContext`: novos campos `todayTaskItems`, `expiringProtocolItems`, `tasksDueCountLabel`, `protocolsExpiringCountLabel`
- `DashboardController`: métodos `updateTodayTasks()` e `updateExpiringProtocols()` chamados a cada refresh
- `AgendaApp`: atalho global `Ctrl/Cmd+S` para "lembrar-me agora" (força check imediato de pendências)
- `AgendaApp`: atalho alternativo `Ctrl/Cmd+Shift+S` para evitar conflito com hábito de salvar
- Barra de status: badge de alerta com animação piscante quando há pendências críticas
- Novo recurso de áudio: `src/main/resources/sounds/reminder.wav`
- Popover no clique do badge da barra de status com breakdown `A/H/P` e ação rápida "Lembrar agora"
- `Agenda e Prioridades`: novo modo de `captura rápida` no formulário de criação (`Nova tarefa`) para registrar tarefa com menos campos (título/data/notas), preenchendo categoria/prioridade/status com padrões seguros
- `Agenda e Prioridades`: destaque visual sutil para estado ativo da `captura rápida`, facilitando identificação do modo simplificado
- `Agenda e Prioridades`: tarefas agora podem ter um `protocolo` associado opcionalmente no formulário completo, sem impactar a `captura rápida`
- `Protocolos Operacionais`: ação rápida `🏠 Protocolo saída de casa` que cria/abre template padrão com itens essenciais (carteira, chave, celular, carregador e complementares)
- `Protocolos Operacionais`: ação rápida `🧳 Protocolo reunião` com template padrão para saídas externas (relatório, documentos, notebook, carregador e itens essenciais)
- `Protocolos Operacionais`: botão `▶ Iniciar selecionado` na toolbar e botão `▶` por linha da lista para abrir execução sem depender de duplo clique
- `Dashboard`: novo card `🏠 Protocolos mais recorrentes` com atalho `Iniciar` direto para protocolos críticos do dia a dia
- `Dashboard`: novo card `⏰ Protocolos de agora` para rotinas imediatamente acionáveis (saída, reunião, remédios, protocolos ligados a tarefa de hoje ou já em execução)
- `Dashboard`: tarefas em destaque e foco principal agora mostram também quando a tarefa possui `🔗 protocolo` associado
- Editor de protocolo: passos agora podem ser reordenados também por arrastar e soltar (drag-and-drop)
- `Dashboard`: tarefas em destaque agora priorizam itens mais recentes e prioritários; pendências muito antigas vão para a seção `🕰 Pendências antigas esquecidas`
- `Dashboard`: duplo clique e ação principal agora podem abrir a `Agenda` já na data exata da tarefa, reduzindo busca manual por itens antigos
- `Dashboard`: pendências antigas mas prioritárias voltam ao foco periodicamente para reduzir esquecimento sem poluir o destaque principal
- `Dashboard`: o bloco principal foi explicitado como `🎯 1 tarefa principal do dia`, ajudando a reduzir paralisia de escolha com um único foco prioritário
- `DatabaseService.listDeadlineAlerts()`: alertas de tarefas atrasadas agora priorizam prioridade e recência, evitando que itens muito antigos dominem a lista principal
- `Agenda e Prioridades`: itens da lista (Dia/Semana/Mês) agora exibem ação direta `🔗` para abrir o protocolo associado da tarefa, além da opção no menu de contexto
- `Protocolos Operacionais`: nova ação `⏰ Protocolos por horário` cria/abre templates de rotina temporal (`Remédio 08:00`, `Remédio 20:00`, `Preparar saída 30 min antes`, `Reunião 1h antes`)
- `Dashboard`: o card `⏰ Protocolos de agora` agora considera gatilhos de horário apenas para protocolos da categoria `Horários` (horário fixo e antecedência "antes")
- Categorias padrão de `Protocolos` expandidas com foco em rotina real: `Horários`, `Rotina diária`, `Saídas e reuniões` e `Medicamentos`
- `Protocolos Operacionais`: formulário ganhou configuração explícita de gatilho por horário (modo, `HH:mm` fixo ou minutos de antecedência), exibida apenas para categoria `Horários`
- `Estudos e Atividades`: novos atalhos `⏸ Pausar` e `▶ Retomar` no painel da lista para mudar status do plano sem entrar em edição completa
- `Dashboard`: novo card `🧠 Captura rápida de ideias` para despejar anotações no impulso e revisar depois sem interromper a atividade atual
- `Ideias e Projetos`: novas capturas podem ir para a categoria `Caixa de entrada`, com revisão posterior por prioridade e duplo clique direto a partir da dashboard
- `Ideias e Projetos`: suporte a hierarquia leve entre anotações/ideias via vínculo `Pertence a`, permitindo relacionar notas filhas a uma ideia-mãe durante a organização
- `Ideias e Projetos`: nova janela `🗂 Revisão da caixa de entrada` com ações rápidas para priorizar, vincular, transformar em projeto, abrir checklist, virar tarefa de hoje e arquivar capturas
- Categorias padrão de `Ideias` agora incluem `Arquivo`, facilitando tirar itens já triados do fluxo ativo sem apagá-los
- `Estudos e Atividades`: frequência agora consciente de pausas — dias em que o plano estava `PAUSADO` aparecem como ⏸ no calendário e não entram no cálculo de faltas
- `Estudos e Atividades`: frequência calculada a partir da data em que o plano ficou ativo pela primeira vez (`EM_ANDAMENTO`), não da data de criação; planos apenas planejados não acumulam faltas
- `Estudos e Atividades`: novo mecanismo de abono de faltas (`ABONADO`) — botão `🎟 Abonar` por falta individual e `🎟 Abonar todas` para justificar em lote sem necessidade de reposição
- `Dashboard`: novo card `📚 Estudos do dia` — exibe estudos com frequência programada para o dia de hoje e permite abrir o Diário Científico com um clique
- `Dashboard`: botão `▶ Abrir Diário` por item do card de estudos e duplo clique abre o diário diretamente da dashboard

### Corrigido
- `AgendaTabController.submitForm()`: adicionado `refreshCurrentView()` e `triggerDashboardRefresh()` após salvar tarefa — lista agora atualiza imediatamente sem trocar de aba
- `theme-dark.css`: cobertura completa do popup do `DatePicker` em modo escuro (botões de navegação de mês, labels, células de dias, dias adjacentes, hoje e selecionado)
- `app.css` / `theme-dark.css`: seleção de linhas em `ListView` e `TableView` agora visível em linhas pares e ímpares (`:filled:selected:odd` / `:filled:selected:even`)
- Badge da barra de status agora usa a mesma base visível da dashboard (Atrasos + Hoje + Protocolos) e mostra breakdown `A/H/P`
- Tooltip no badge de status detalha o significado de `A/H/P` e mostra os totais atuais
- `Agenda e Prioridades`: painel de formulário agora é preview-first; seleção da lista carrega pré-visualização read-only, edição só via botão `Editar selecionada`, `Esc` cancela edição/criação, e ação primária alterna entre `Nova tarefa` e `+ Adicionar tarefa`
- Badge da barra de status agora mostra `PENDÊNCIAS` (não confunde com "atrasos") e usa cor de aviso quando `A=0` (apenas hoje/protocolos)
- Dashboard: painéis/listas de "Alertas de atraso" e "Próximos prazos" ganharam alturas mínimas e VGrow para não ficarem invisíveis quando a área superior cresce
- Dashboard: conteúdo da aba agora fica dentro de `ScrollPane` vertical para permitir rolagem quando os painéis ultrapassam a altura da janela

---

## [1.1.0] — 2026-05-13

### Corrigido
- SSL `handshake_failure` no Windows: forçado TLS 1.2/1.3 em `GoogleAuthService` e `GoogleTasksService`

---

## [1.0.0] — Inicial

### Adicionado
- Agenda com tarefas do dia, filtro por mês e marcação de concluídas
- Alertas de atrasos (tarefas vencidas e pagamentos vencidos)
- Checklists de protocolos e ações (Protocolos Operacionais)
- Financeiro (orçamentos, pendências, lançamentos)
- Vendas pessoais e controle básico de estoque
- Frequência de estudos e atividades
- Banco de ideias para projetos pessoais
- Temas claro e escuro com token CSS globais (`-t-*`)
- Dashboard com KPIs consolidados
- Exportação iCalendar (.ics) para Google Calendar
- Integração Google Tasks
- Recorrência de tarefas: `SINGLE`, `RANGE`, `WEEKLY`
- Sistema de timer de sessões por tarefa
- Impressão de relatórios





