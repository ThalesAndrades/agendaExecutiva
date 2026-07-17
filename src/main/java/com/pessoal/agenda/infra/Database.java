package com.pessoal.agenda.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/**
 * Infraestrutura de banco de dados SQLite.
 * Responsavel por fornecer conexoes e executar a migracao de schema.
 * Todos os repositorios dependem desta classe.
 */
public class Database {

    private final String jdbcUrl;

    public Database() {
        this.jdbcUrl = resolveJdbcUrl();
    }

    /**
     * Construtor com URL JDBC explícita. Útil para testes de integração
     * (ex.: um arquivo SQLite temporário) sem tocar no banco real do usuário.
     */
    public Database(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    private String resolveJdbcUrl() {
        Path appDir = Path.of(System.getProperty("user.home"), ".agenda-pessoal");
        try {
            Files.createDirectories(appDir);
        } catch (IOException e) {
            throw new RuntimeException("Nao foi possivel criar diretorio de dados", e);
        }
        return "jdbc:sqlite:" + appDir.resolve("agenda.db");
    }

    /** Abre uma nova conexao JDBC. O chamador e responsavel por fechar. */
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    // ── Helpers compartilhados por repositorios ────────────────────────────

    /** Executa um UPDATE/INSERT/DELETE com parametros posicionais. */
    public void execute(String sql, Object... params) {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao executar SQL: " + sql, e);
        }
    }

    /** Executa um SELECT e retorna o primeiro inteiro da primeira linha. */
    public int queryInt(String sql, Object... params) {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao consultar (int): " + sql, e);
        }
    }

    /** Executa um SELECT e retorna o primeiro double da primeira linha. */
    public double queryDouble(String sql, Object... params) {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao consultar (double): " + sql, e);
        }
    }

    private void bindParams(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    // ── Migracao de schema ─────────────────────────────────────────────────

    /** Cria tabelas e aplica migrações incrementais. Idempotente. */
    public void runMigrations() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    notes TEXT,
                    due_date TEXT NOT NULL,
                    done INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS checklist_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    protocol_name TEXT NOT NULL,
                    item_text TEXT NOT NULL,
                    done INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS finance_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entry_type TEXT NOT NULL,
                    description TEXT NOT NULL,
                    amount REAL NOT NULL,
                    due_date TEXT,
                    paid INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sales_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_name TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    unit_price REAL NOT NULL,
                    sale_date TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS inventory_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_name TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    minimum_quantity INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS study_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    subject TEXT NOT NULL,
                    session_date TEXT NOT NULL,
                    duration_minutes INTEGER NOT NULL,
                    notes TEXT,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS project_ideas (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    description TEXT,
                    status TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            // Tabela de categorias personalizadas por dominio
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    domain TEXT NOT NULL,
                    color TEXT,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(name, domain)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS study_plans (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    study_type TEXT NOT NULL DEFAULT 'GERAL',
                    category TEXT NOT NULL DEFAULT 'Geral',
                    description TEXT,
                    start_date TEXT,
                    target_date TEXT,
                    status TEXT NOT NULL DEFAULT 'PLANEJADO',
                    total_pages INTEGER NOT NULL DEFAULT 0,
                    current_page INTEGER NOT NULL DEFAULT 0,
                    progress_percent REAL NOT NULL DEFAULT 0.0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS study_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    study_id INTEGER NOT NULL,
                    entry_title TEXT,
                    entry_date TEXT NOT NULL,
                    content TEXT,
                    duration_minutes INTEGER NOT NULL DEFAULT 0,
                    page_start INTEGER NOT NULL DEFAULT 0,
                    page_end INTEGER NOT NULL DEFAULT 0,
                    entry_order INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            // ── Protocolos Operacionais ────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS protocols (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    execution_type TEXT NOT NULL DEFAULT 'RECORRENTE',
                    category TEXT NOT NULL DEFAULT 'Geral',
                    description TEXT,
                    linked_task_id INTEGER,
                    validity_days INTEGER NOT NULL DEFAULT 0,
                    timing_mode TEXT NOT NULL DEFAULT 'NONE',
                    fixed_time TEXT,
                    lead_minutes INTEGER,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS protocol_steps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    template_id INTEGER NOT NULL,
                    step_order INTEGER NOT NULL DEFAULT 0,
                    step_text TEXT NOT NULL,
                    notes TEXT,
                    critical INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS protocol_executions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    template_id INTEGER NOT NULL,
                    iteration_number INTEGER NOT NULL DEFAULT 1,
                    started_at TEXT NOT NULL,
                    completed_at TEXT,
                    status TEXT NOT NULL DEFAULT 'ATIVA',
                    notes TEXT,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS protocol_execution_steps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    execution_id INTEGER NOT NULL,
                    step_id INTEGER NOT NULL,
                    step_text TEXT NOT NULL,
                    step_notes TEXT,
                    critical INTEGER NOT NULL DEFAULT 0,
                    step_order INTEGER NOT NULL DEFAULT 0,
                    checked INTEGER NOT NULL DEFAULT 0,
                    checked_at TEXT,
                    observation_notes TEXT,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            // Protocolos: validade em dias (coluna pode já existir)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS study_schedules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    study_plan_id INTEGER NOT NULL,
                    day_of_week INTEGER NOT NULL,
                    min_minutes INTEGER NOT NULL DEFAULT 30,
                    UNIQUE(study_plan_id, day_of_week)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS study_compensations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    study_plan_id INTEGER NOT NULL,
                    missed_date TEXT NOT NULL,
                    compensation_date TEXT,
                    compensation_minutes INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL DEFAULT 'PENDENTE',
                    notes TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS study_plan_status_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    study_plan_id INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    changed_at TEXT NOT NULL DEFAULT (date('now'))
                )""");

            // ── Checklist de Próximas Ações (Ideias / Projetos) ───────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS idea_checklist_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    idea_id INTEGER NOT NULL,
                    text TEXT NOT NULL DEFAULT '',
                    done INTEGER NOT NULL DEFAULT 0,
                    position INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

            // ── Checklist de Tarefas da Agenda ─────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS task_checklist_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id INTEGER NOT NULL,
                    text TEXT NOT NULL DEFAULT '',
                    done INTEGER NOT NULL DEFAULT 0,
                    position INTEGER NOT NULL DEFAULT 0,
                    kanban_column TEXT NOT NULL DEFAULT 'backlog',
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inicializar schema do banco", e);
        }

        // Migracoes incrementais — seguras para re-execucao
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN category TEXT NOT NULL DEFAULT 'Geral'");
        // Agendamento avancado (Palm-style): tipo, fim e dias da semana
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN schedule_type TEXT NOT NULL DEFAULT 'SINGLE'");
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN end_date TEXT");
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN recurrence_days TEXT");
        // Categorias para checklist, estudos e ideias
        applyAlterIfMissing("ALTER TABLE checklist_items ADD COLUMN category TEXT NOT NULL DEFAULT 'Geral'");
        applyAlterIfMissing("ALTER TABLE study_sessions ADD COLUMN category TEXT NOT NULL DEFAULT 'Geral'");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN category TEXT NOT NULL DEFAULT 'Geral'");
        // Horário, prioridade e status nas tarefas
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN start_time TEXT");
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN end_time TEXT");
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN priority TEXT NOT NULL DEFAULT 'NORMAL'");
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDENTE'");
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN linked_protocol_id INTEGER");
        // Analytics de produtividade: momento em que a tarefa foi concluída
        applyAlterIfMissing("ALTER TABLE tasks ADD COLUMN completed_at TEXT");
        // Protocolos: validade em dias
        applyAlterIfMissing("ALTER TABLE protocols ADD COLUMN validity_days INTEGER NOT NULL DEFAULT 0");
        applyAlterIfMissing("ALTER TABLE protocols ADD COLUMN timing_mode TEXT NOT NULL DEFAULT 'NONE'");
        applyAlterIfMissing("ALTER TABLE protocols ADD COLUMN fixed_time TEXT");
        applyAlterIfMissing("ALTER TABLE protocols ADD COLUMN lead_minutes INTEGER");
        // Ideias e Projetos: campos avancados (pipeline cientifico)
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN priority TEXT NOT NULL DEFAULT 'NORMAL'");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN idea_type TEXT NOT NULL DEFAULT 'GERAL'");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN impact_level TEXT NOT NULL DEFAULT 'MEDIO'");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN feasibility INTEGER NOT NULL DEFAULT 3");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN estimated_hours INTEGER NOT NULL DEFAULT 0");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN start_date TEXT");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN target_date TEXT");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN methodology TEXT");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN next_actions TEXT");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN keywords TEXT");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN references_text TEXT");
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN parent_idea_id INTEGER");
        // Modo da seção Próximas Ações: 'text' ou 'checklist'
        applyAlterIfMissing("ALTER TABLE project_ideas ADD COLUMN next_actions_mode TEXT NOT NULL DEFAULT 'text'");
        // Vendas e Estoque: suporte a materiais e serviços
        applyAlterIfMissing("ALTER TABLE sales_entries ADD COLUMN item_type TEXT NOT NULL DEFAULT 'material'");
        applyAlterIfMissing("ALTER TABLE sales_entries ADD COLUMN client_name TEXT");
        applyAlterIfMissing("ALTER TABLE sales_entries ADD COLUMN notes TEXT");
        applyAlterIfMissing("ALTER TABLE sales_entries ADD COLUMN status TEXT NOT NULL DEFAULT 'recebido'");
        applyAlterIfMissing("ALTER TABLE inventory_items ADD COLUMN item_type TEXT NOT NULL DEFAULT 'material'");
        applyAlterIfMissing("ALTER TABLE inventory_items ADD COLUMN unit_price REAL NOT NULL DEFAULT 0");
        applyAlterIfMissing("ALTER TABLE inventory_items ADD COLUMN category TEXT NOT NULL DEFAULT 'Geral'");
        applyAlterIfMissing("ALTER TABLE inventory_items ADD COLUMN description TEXT");
        // Checklist de Ideias/Projetos: coluna Kanban
        applyAlterIfMissing("ALTER TABLE idea_checklist_items ADD COLUMN kanban_column TEXT NOT NULL DEFAULT 'backlog'");
        // Checklist de Tarefas da Agenda: coluna kanban (já na tabela, mas garante compatibilidade)
        applyAlterIfMissing("ALTER TABLE task_checklist_items ADD COLUMN kanban_column TEXT NOT NULL DEFAULT 'backlog'");

        // Google Tasks: tabela de mapeamento local ↔ Google
        applyCreateIfMissing("""
            CREATE TABLE IF NOT EXISTS google_tasks_mapping (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                local_task_id  INTEGER NOT NULL,
                google_list_id TEXT    NOT NULL,
                google_task_id TEXT    NOT NULL,
                last_synced_at TEXT    NOT NULL DEFAULT (datetime('now')),
                UNIQUE(local_task_id),
                UNIQUE(google_list_id, google_task_id)
            )""");
    }

    /** Executa CREATE TABLE IF NOT EXISTS — usa o mesmo mecanismo idempotente. */
    private void applyCreateIfMissing(String createSql) {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
        } catch (SQLException ignored) { /* já existe */ }
    }

    private void applyAlterIfMissing(String alterSql) {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(alterSql);
        } catch (SQLException e) {
            // Ignora apenas o caso esperado (coluna já existe). Falhas reais —
            // banco bloqueado, corrupção, SQL inválido — devem ser propagadas
            // para não deixar o schema silenciosamente incompleto.
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (!msg.contains("duplicate column name")) {
                throw new RuntimeException("Erro na migração de schema: " + alterSql, e);
            }
        }
    }
}

