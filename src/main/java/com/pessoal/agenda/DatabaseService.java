package com.pessoal.agenda;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class DatabaseService {
    private final String jdbcUrl;

    public static final List<String> TASK_CATEGORIES = List.of(
            "Geral", "Pesquisa", "Experimento", "Leitura",
            "Escrita", "Reunião", "Administração", "Saúde", "Pessoal", "Projeto"
    );

    public DatabaseService() {
        this.jdbcUrl = "jdbc:sqlite:" + buildDatabasePath();
    }

    private String buildDatabasePath() {
        Path appDir = Path.of(System.getProperty("user.home"), ".agenda-pessoal");
        try {
            Files.createDirectories(appDir);
        } catch (IOException e) {
            throw new RuntimeException("Nao foi possivel criar pasta de dados", e);
        }
        return appDir.resolve("agenda.db").toString();
    }

    public void initialize() {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL,
                        notes TEXT,
                        due_date TEXT NOT NULL,
                        done INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS checklist_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        protocol_name TEXT NOT NULL,
                        item_text TEXT NOT NULL,
                        done INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS finance_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        entry_type TEXT NOT NULL,
                        description TEXT NOT NULL,
                        amount REAL NOT NULL,
                        due_date TEXT,
                        paid INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sales_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        product_name TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        unit_price REAL NOT NULL,
                        sale_date TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS inventory_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        product_name TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        minimum_quantity INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS study_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        subject TEXT NOT NULL,
                        session_date TEXT NOT NULL,
                        duration_minutes INTEGER NOT NULL,
                        notes TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS project_ideas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL,
                        description TEXT,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inicializar banco SQLite", e);
        }
        ensureTaskCategoryColumn();
        ensureColumn("ALTER TABLE tasks ADD COLUMN schedule_type TEXT NOT NULL DEFAULT 'SINGLE'");
        ensureColumn("ALTER TABLE tasks ADD COLUMN end_date TEXT");
        ensureColumn("ALTER TABLE tasks ADD COLUMN recurrence_days TEXT");
        ensureColumn("ALTER TABLE tasks ADD COLUMN linked_protocol_id INTEGER");
        ensureColumn("ALTER TABLE checklist_items ADD COLUMN category TEXT NOT NULL DEFAULT 'Geral'");
        ensureColumn("ALTER TABLE study_sessions ADD COLUMN category TEXT NOT NULL DEFAULT 'Geral'");
        ensureColumn("ALTER TABLE project_ideas ADD COLUMN category TEXT NOT NULL DEFAULT 'Geral'");
        ensureColumn("ALTER TABLE project_ideas ADD COLUMN parent_idea_id INTEGER");
    }

    private void ensureTaskCategoryColumn() {
        ensureColumn("ALTER TABLE tasks ADD COLUMN category TEXT NOT NULL DEFAULT 'Geral'");
    }

    private void ensureColumn(String alterSql) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(alterSql);
        } catch (SQLException ignored) { /* coluna ja existe */ }
    }

    public void addTask(String title, String notes, LocalDate dueDate, String category) {
        addTask(title, notes, dueDate, category, "SINGLE", null, null);
    }

    public void addTask(String title, String notes, LocalDate dueDate) {
        addTask(title, notes, dueDate, "Geral");
    }

    /**
     * Registra tarefa com agendamento completo.
     *
     * @param scheduleType   "SINGLE" | "RANGE" | "WEEKLY"
     * @param endDate        fim do intervalo (RANGE/WEEKLY); null para SINGLE
     * @param recurrenceDays dias ativos para WEEKLY, formato strftime('%w'): "1,3,5" = Seg/Qua/Sex
     */
    public void addTask(String title, String notes, LocalDate dueDate, String category,
                        String scheduleType, LocalDate endDate, String recurrenceDays) {
        executeUpdate(
            "INSERT INTO tasks(title, notes, due_date, done, category, schedule_type, end_date, recurrence_days)"
            + " VALUES(?, ?, ?, 0, ?, ?, ?, ?)",
            title, notes, dueDate.toString(),
            category == null ? "Geral" : category,
            scheduleType != null ? scheduleType : "SINGLE",
            endDate != null ? endDate.toString() : null,
            recurrenceDays);
    }

    public List<RowItem> listTasksByMonth(YearMonth month) {
        return listTasksByMonthFiltered(month, null);
    }

    public List<RowItem> listTasksByMonthFiltered(YearMonth month, String categoryFilter) {
        boolean hasFilter = categoryFilter != null && !categoryFilter.isBlank();
        String start = month.atDay(1).toString();
        String end   = month.atEndOfMonth().toString();
        // Tarefas SINGLE no mes OU tarefas RANGE/WEEKLY que se sobrepoem ao mes
        String sql = "SELECT id, title, due_date, done, category, schedule_type, end_date FROM tasks "
                + "WHERE ("
                + "  (schedule_type = 'SINGLE' AND due_date >= ? AND due_date <= ?)"
                + "  OR (schedule_type IN ('RANGE','WEEKLY') AND due_date <= ? AND end_date >= ?)"
                + ")"
                + (hasFilter ? " AND category = ?" : "")
                + " ORDER BY due_date ASC, done ASC, id ASC";
        List<RowItem> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, start);
            statement.setString(2, end);
            statement.setString(3, end);
            statement.setString(4, start);
            if (hasFilter) statement.setString(5, categoryFilter);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String mark       = rs.getInt("done") == 1 ? "✓" : "○";
                    String schedType  = rs.getString("schedule_type");
                    String endDateStr = rs.getString("end_date");
                    String prefix     = schedulePrefix(schedType);
                    String dateDisplay = rs.getString("due_date");
                    if (endDateStr != null && !"SINGLE".equals(schedType)) {
                        dateDisplay = dateDisplay + " → " + endDateStr;
                    }
                    rows.add(new RowItem(rs.getLong("id"),
                            "%s  [%s]  %s%s  —  %s".formatted(
                                    mark, rs.getString("category"),
                                    prefix, rs.getString("title"), dateDisplay)));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar tarefas", e);
        }
        return rows;
    }

    public List<RowItem> listTasksByDay(LocalDate date, String categoryFilter) {
        boolean hasFilter = categoryFilter != null && !categoryFilter.isBlank();
        String d = date.toString();
        // Clausula recorrente: SINGLE exato, RANGE intervalo, WEEKLY intervalo + dia da semana
        String sql = "SELECT id, title, done, category, schedule_type FROM tasks WHERE ("
                + "  (schedule_type = 'SINGLE' AND due_date = ?)"
                + "  OR (schedule_type = 'RANGE' AND due_date <= ? AND end_date >= ?)"
                + "  OR (schedule_type = 'WEEKLY' AND due_date <= ? AND end_date >= ?"
                + "      AND instr(',' || recurrence_days || ',', ',' || CAST(strftime('%w', ?) AS TEXT) || ',') > 0)"
                + ")"
                + (hasFilter ? " AND category = ?" : "")
                + " ORDER BY done ASC, category ASC, id ASC";
        List<RowItem> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, d);
            statement.setString(2, d);
            statement.setString(3, d);
            statement.setString(4, d);
            statement.setString(5, d);
            statement.setString(6, d);
            if (hasFilter) statement.setString(7, categoryFilter);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String mark   = rs.getInt("done") == 1 ? "✓" : "○";
                    String prefix = schedulePrefix(rs.getString("schedule_type"));
                    rows.add(new RowItem(rs.getLong("id"),
                            "%s  [%s]  %s%s".formatted(mark, rs.getString("category"),
                                    prefix, rs.getString("title"))));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar tarefas do dia", e);
        }
        return rows;
    }

    public record MonthSummary(int month, String monthName, int total, int open) {}

    public List<MonthSummary> getYearOverview(int year, String categoryFilter) {
        boolean hasFilter = categoryFilter != null && !categoryFilter.isBlank();
        String sql = """
                SELECT CAST(strftime('%m', due_date) AS INTEGER) as m,
                       COUNT(*) as total,
                       SUM(CASE WHEN done = 0 THEN 1 ELSE 0 END) as open_count
                FROM tasks
                WHERE due_date >= ? AND due_date <= ?
                """ + (hasFilter ? " AND category = ?" : "") + " GROUP BY m";
        int[] totals = new int[13];
        int[] opens = new int[13];
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, LocalDate.of(year, 1, 1).toString());
            statement.setString(2, LocalDate.of(year, 12, 31).toString());
            if (hasFilter) statement.setString(3, categoryFilter);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int m = rs.getInt("m");
                    if (m >= 1 && m <= 12) {
                        totals[m] = rs.getInt("total");
                        opens[m] = rs.getInt("open_count");
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao obter visao anual", e);
        }
        String[] names = {"Janeiro", "Fevereiro", "Marco", "Abril", "Maio", "Junho",
                "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"};
        List<MonthSummary> result = new ArrayList<>();
        for (int m = 1; m <= 12; m++) result.add(new MonthSummary(m, names[m - 1], totals[m], opens[m]));
        return result;
    }

    public void markTaskDone(long taskId) {
        executeUpdate("UPDATE tasks SET done = 1, status = 'CONCLUIDA', "
                + "completed_at = COALESCE(completed_at, datetime('now','localtime')) WHERE id = ?", taskId);
    }

    public void addChecklistItem(String protocolName, String itemText) {
        executeUpdate(
                "INSERT INTO checklist_items(protocol_name, item_text, done) VALUES(?, ?, 0)",
                protocolName,
                itemText
        );
    }

    public List<RowItem> listChecklistItems() { return listChecklistItems(null); }

    public List<RowItem> listChecklistItems(String categoryFilter) {
        boolean hasFilter = categoryFilter != null && !categoryFilter.isBlank();
        String sql = "SELECT id, protocol_name, item_text, done, category FROM checklist_items"
                + (hasFilter ? " WHERE category=?" : "")
                + " ORDER BY done ASC, id DESC";
        List<RowItem> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (hasFilter) statement.setString(1, categoryFilter);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String status = resultSet.getInt("done") == 1 ? "OK" : "Pendente";
                    String cat    = resultSet.getString("category");
                    rows.add(new RowItem(resultSet.getLong("id"),
                            "[%s] [%s] %s → %s".formatted(
                                    status, cat != null ? cat : "Geral",
                                    resultSet.getString("protocol_name"),
                                    resultSet.getString("item_text"))));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar checklist", e);
        }
        return rows;
    }

    public void markChecklistDone(long itemId) {
        executeUpdate("UPDATE checklist_items SET done = 1 WHERE id = ?", itemId);
    }

    public void addFinanceEntry(String type, String description, double amount, LocalDate dueDate, boolean paid) {
        executeUpdate(
                "INSERT INTO finance_entries(entry_type, description, amount, due_date, paid) VALUES(?, ?, ?, ?, ?)",
                type,
                description,
                amount,
                dueDate != null ? dueDate.toString() : null,
                paid ? 1 : 0
        );
    }

    public List<RowItem> listFinanceEntries() {
        String sql = "SELECT id, entry_type, description, amount, due_date, paid FROM finance_entries ORDER BY id DESC";
        List<RowItem> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String status = resultSet.getInt("paid") == 1 ? "Pago" : "Pendente";
                rows.add(new RowItem(resultSet.getLong("id"),
                        "%s | %s | R$ %.2f | Venc.: %s | %s".formatted(
                                resultSet.getString("entry_type"),
                                resultSet.getString("description"),
                                resultSet.getDouble("amount"),
                                safeText(resultSet.getString("due_date")),
                                status
                        )));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar financeiro", e);
        }
        return rows;
    }

    public void markFinancePaid(long financeId) {
        executeUpdate("UPDATE finance_entries SET paid = 1 WHERE id = ?", financeId);
    }

    public void addSaleEntry(String productName, int quantity, double unitPrice, LocalDate saleDate) {
        executeUpdate(
                "INSERT INTO sales_entries(product_name, quantity, unit_price, sale_date) VALUES(?, ?, ?, ?)",
                productName,
                quantity,
                unitPrice,
                saleDate.toString()
        );
    }

    public List<RowItem> listSalesEntries() {
        String sql = "SELECT id, product_name, quantity, unit_price, sale_date FROM sales_entries ORDER BY id DESC";
        List<RowItem> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                double total = resultSet.getInt("quantity") * resultSet.getDouble("unit_price");
                rows.add(new RowItem(resultSet.getLong("id"),
                        "%s | Qtd: %d | Unit: R$ %.2f | Total: R$ %.2f | Data: %s".formatted(
                                resultSet.getString("product_name"),
                                resultSet.getInt("quantity"),
                                resultSet.getDouble("unit_price"),
                                total,
                                resultSet.getString("sale_date")
                        )));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar vendas", e);
        }
        return rows;
    }

    public void addInventoryItem(String productName, int quantity, int minimumQuantity) {
        executeUpdate(
                "INSERT INTO inventory_items(product_name, quantity, minimum_quantity) VALUES(?, ?, ?)",
                productName,
                quantity,
                minimumQuantity
        );
    }

    public List<RowItem> listInventoryItems() {
        String sql = "SELECT id, product_name, quantity, minimum_quantity FROM inventory_items ORDER BY product_name ASC";
        List<RowItem> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                int qtd = resultSet.getInt("quantity");
                int min = resultSet.getInt("minimum_quantity");
                String alert = qtd <= min ? "ATENCAO BAIXO" : "OK";
                rows.add(new RowItem(resultSet.getLong("id"),
                        "%s | Estoque: %d | Min.: %d | %s".formatted(
                                resultSet.getString("product_name"),
                                qtd,
                                min,
                                alert
                        )));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar estoque", e);
        }
        return rows;
    }

    public void addStudySession(String subject, LocalDate sessionDate, int durationMinutes, String notes) {
        executeUpdate(
                "INSERT INTO study_sessions(subject, session_date, duration_minutes, notes) VALUES(?, ?, ?, ?)",
                subject,
                sessionDate.toString(),
                durationMinutes,
                notes
        );
    }

    public List<RowItem> listStudySessions() { return listStudySessions(null); }

    public List<RowItem> listStudySessions(String categoryFilter) {
        boolean hasFilter = categoryFilter != null && !categoryFilter.isBlank();
        String sql = "SELECT id, subject, session_date, duration_minutes, notes, category FROM study_sessions"
                + (hasFilter ? " WHERE category=?" : "")
                + " ORDER BY session_date DESC";
        List<RowItem> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (hasFilter) statement.setString(1, categoryFilter);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String cat = resultSet.getString("category");
                    rows.add(new RowItem(resultSet.getLong("id"),
                            "[%s] %s | Data: %s | %d min | %s".formatted(
                                    cat != null ? cat : "Geral",
                                    resultSet.getString("subject"),
                                    resultSet.getString("session_date"),
                                    resultSet.getInt("duration_minutes"),
                                    safeText(resultSet.getString("notes")))));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar estudos", e);
        }
        return rows;
    }

    public void addProjectIdea(String title, String description, String status) {
        executeUpdate(
                "INSERT INTO project_ideas(title, description, status) VALUES(?, ?, ?)",
                title,
                description,
                status
        );
    }

    public List<RowItem> listProjectIdeas() { return listProjectIdeas(null); }

    public List<RowItem> listProjectIdeas(String categoryFilter) {
        boolean hasFilter = categoryFilter != null && !categoryFilter.isBlank();
        String sql = "SELECT id, title, description, status, category FROM project_ideas"
                + (hasFilter ? " WHERE category=?" : "")
                + " ORDER BY id DESC";
        List<RowItem> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (hasFilter) statement.setString(1, categoryFilter);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String cat = resultSet.getString("category");
                    rows.add(new RowItem(resultSet.getLong("id"),
                            "[%s] [%s] %s | %s".formatted(
                                    resultSet.getString("status"),
                                    cat != null ? cat : "Geral",
                                    resultSet.getString("title"),
                                    safeText(resultSet.getString("description")))));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar ideias", e);
        }
        return rows;
    }

    public List<String> listDeadlineAlerts() {
        List<String> alerts = new ArrayList<>();

        String overdueTasksSql = """
                SELECT title, due_date, schedule_type, end_date, priority,
                       CASE
                         WHEN schedule_type = 'SINGLE' THEN due_date
                         ELSE COALESCE(end_date, due_date)
                       END AS alert_date
                FROM tasks
                WHERE done = 0 AND status != 'CANCELADA' AND (
                  (schedule_type = 'SINGLE' AND due_date < date('now'))
                  OR (schedule_type IN ('RANGE','WEEKLY') AND end_date IS NOT NULL AND end_date < date('now'))
                )
                ORDER BY CASE priority
                           WHEN 'CRITICA' THEN 0
                           WHEN 'ALTA' THEN 1
                           WHEN 'NORMAL' THEN 2
                           ELSE 3
                         END ASC,
                         alert_date DESC
                """;

        String overduePaymentsSql = """
                SELECT description, due_date FROM finance_entries
                WHERE paid = 0 AND due_date IS NOT NULL AND due_date < date('now')
                ORDER BY due_date ASC
                """;

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            try (PreparedStatement ps = connection.prepareStatement(overdueTasksSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String d = "SINGLE".equals(rs.getString("schedule_type"))
                            ? rs.getString("due_date")
                            : rs.getString("due_date") + " → " + rs.getString("end_date");
                    alerts.add("Tarefa atrasada: %s (venc.: %s)".formatted(rs.getString("title"), d));
                }
            }

            try (PreparedStatement ps = connection.prepareStatement(overduePaymentsSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    alerts.add("Pagamento pendente: %s (venc.: %s)".formatted(rs.getString("description"), rs.getString("due_date")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar alertas", e);
        }

        if (alerts.isEmpty()) {
            alerts.add("Sem atrasos no momento.");
        }

        return alerts;
    }

    public int countOpenTasks() {
        return queryForInt("SELECT COUNT(*) FROM tasks WHERE done = 0 AND status != 'CANCELADA'");
    }

    public int countOverdueTasks() {
        return queryForInt("""
            SELECT COUNT(*) FROM tasks WHERE done = 0 AND status != 'CANCELADA' AND (
              (schedule_type = 'SINGLE' AND due_date < date('now'))
              OR (schedule_type IN ('RANGE','WEEKLY') AND end_date IS NOT NULL AND end_date < date('now'))
            )""");
    }

    public int countPendingChecklistItems() {
        return queryForInt("SELECT COUNT(*) FROM checklist_items WHERE done = 0");
    }

    public int countPendingPayments() {
        return queryForInt("SELECT COUNT(*) FROM finance_entries WHERE paid = 0");
    }

    public double sumPendingPayments() {
        return queryForDouble("SELECT COALESCE(SUM(amount), 0) FROM finance_entries WHERE paid = 0");
    }

    public int countLowStockItems() {
        return queryForInt("SELECT COUNT(*) FROM inventory_items WHERE quantity <= minimum_quantity");
    }

    public int countIdeasInProgress() {
        return queryForInt("SELECT COUNT(*) FROM project_ideas WHERE status <> 'concluida'");
    }

    public int totalStudyMinutesInMonth(YearMonth month) {
        String sql = """
                SELECT COALESCE(SUM(duration_minutes), 0)
                FROM study_sessions
                WHERE session_date >= ? AND session_date <= ?
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, month.atDay(1).toString());
            statement.setString(2, month.atEndOfMonth().toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao consolidar estudo mensal", e);
        }
        return 0;
    }

    public List<String> listUpcomingDeadlines(int limit) {
        String sql = """
                SELECT due_date, title, source
                FROM (
                    SELECT due_date, title, 'Tarefa' AS source
                    FROM tasks
                    WHERE done = 0 AND (
                      (schedule_type = 'SINGLE' AND due_date >= date('now'))
                      OR (schedule_type IN ('RANGE','WEEKLY') AND end_date >= date('now'))
                    )
                    UNION ALL
                    SELECT due_date, description AS title, 'Pagamento' AS source
                    FROM finance_entries
                    WHERE paid = 0 AND due_date IS NOT NULL AND due_date >= date('now')
                )
                ORDER BY due_date ASC
                LIMIT ?
                """;

        List<String> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add("%s | %s | %s".formatted(
                            resultSet.getString("due_date"),
                            resultSet.getString("source"),
                            resultSet.getString("title")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar proximos prazos", e);
        }

        if (rows.isEmpty()) {
            rows.add("Sem prazos futuros registrados.");
        }
        return rows;
    }

    private int queryForInt(String sql) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao consultar indicador", e);
        }
        return 0;
    }

    private double queryForDouble(String sql) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getDouble(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao consultar indicador financeiro", e);
        }
        return 0D;
    }

    private void executeUpdate(String sql, Object... values) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro de persistencia no SQLite", e);
        }
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /** Prefixo visual para indicar tipo de agendamento na lista. */
    private String schedulePrefix(String scheduleType) {
        if (scheduleType == null) return "";
        return switch (scheduleType) {
            case "RANGE"  -> "↔ ";
            case "WEEKLY" -> "↺ ";
            default        -> "";
        };
    }

    public record RowItem(long id, String text) {
        @Override
        public String toString() {
            return text;
        }
    }

    // Utilitário: busca Task por id (para uso no controller)
    public com.pessoal.agenda.model.Task findTaskById(Long id) {
        // TODO: Ajuste para o construtor real de Task
        return null;
    }

    // Utilitário: retorna instância de TaskSessionRepository
    public com.pessoal.agenda.repository.TaskSessionRepository getTaskSessionRepository() {
        return new com.pessoal.agenda.repository.TaskSessionRepository(new com.pessoal.agenda.infra.Database());
    }
}
