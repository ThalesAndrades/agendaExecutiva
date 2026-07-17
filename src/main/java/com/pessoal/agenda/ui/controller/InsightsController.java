package com.pessoal.agenda.ui.controller;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.model.ScheduleType;
import com.pessoal.agenda.model.TaskPriority;
import com.pessoal.agenda.model.TaskStatus;
import com.pessoal.agenda.service.InsightsEngine;
import com.pessoal.agenda.service.NaturalLanguageTaskParser;
import com.pessoal.agenda.service.TaskPrioritizer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Aba "🧠 Inteligência" — camada analítica e de captura inteligente da agenda.
 *
 * Reúne, em um único lugar:
 * <ul>
 *   <li><b>Captura inteligente</b>: uma caixa de texto em linguagem natural que
 *       interpreta data, hora, prioridade e categoria automaticamente.</li>
 *   <li><b>Indicadores de produtividade</b>: vazão, sequência (streak),
 *       concluídas na semana/mês, abertas e atrasadas.</li>
 *   <li><b>Foco do dia</b> e <b>prioridades inteligentes</b> pontuadas.</li>
 *   <li><b>Recomendações</b> acionáveis baseadas no estado atual.</li>
 * </ul>
 */
public class InsightsController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SharedContext ctx;

    private BiConsumer<LocalDate, Long> taskNavigator;

    // ── Captura inteligente ────────────────────────────────────────────────
    private final TextField captureField = new TextField();
    private final Label capturePreview = new Label();

    // ── Indicadores ────────────────────────────────────────────────────────
    private final Label throughputValue    = new Label("100%");
    private final Label streakValue         = new Label("0");
    private final Label completedWeekValue  = new Label("0");
    private final Label completedMonthValue = new Label("0");
    private final Label openValue           = new Label("0");
    private final Label overdueValue        = new Label("0");

    // ── Foco do dia ────────────────────────────────────────────────────────
    private final Label focusTitle  = new Label("—");
    private final Label focusReason = new Label("");
    private final Button focusOpenBtn = new Button("Abrir na Agenda");
    private Long focusTaskId;
    private LocalDate focusDate;

    // ── Listas ─────────────────────────────────────────────────────────────
    private final ObservableList<PriorityItem> priorityItems = FXCollections.observableArrayList();
    private final ObservableList<String> recommendationItems = FXCollections.observableArrayList();

    private record PriorityItem(long taskId, LocalDate anchor, String title,
                                String reason, int score, String color) {}

    public InsightsController(SharedContext ctx) {
        this.ctx = ctx;
    }

    /** Permite abrir uma tarefa diretamente na aba Agenda (data + id). */
    public void setTaskNavigator(BiConsumer<LocalDate, Long> navigator) {
        this.taskNavigator = navigator;
    }

    // ── Construção da aba ──────────────────────────────────────────────────

    public Tab buildTab() {
        Tab tab = new Tab("🧠 Inteligência");
        tab.setClosable(false);

        VBox content = new VBox(14,
                buildCaptureCard(),
                buildMetricsCard(),
                buildFocusCard(),
                UIHelper.createCardSection("🔝 Prioridades inteligentes", buildPriorityList()),
                UIHelper.createCardSection("💡 Recomendações", buildRecommendationList()));
        content.setPadding(new Insets(16));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("insights-scroll");
        tab.setContent(scroll);
        return tab;
    }

    private VBox buildCaptureCard() {
        captureField.setPromptText("Ex.: Pagar aluguel amanhã às 14h #financeiro !alta");
        captureField.getStyleClass().add("input-control");
        HBox.setHgrow(captureField, Priority.ALWAYS);
        captureField.textProperty().addListener((o, a, b) -> updatePreview());
        captureField.setOnAction(e -> doCapture());

        Button captureBtn = new Button("➕ Capturar");
        captureBtn.getStyleClass().add("primary-button");
        captureBtn.setOnAction(e -> doCapture());

        HBox inputRow = new HBox(10, captureField, captureBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        capturePreview.getStyleClass().add("kpi-title");
        capturePreview.setWrapText(true);
        updatePreview();

        Label hint = new Label("Reconhece: hoje, amanhã, dia da semana, dia N, DD/MM, "
                + "\"em N dias\", horários (14h, 15:30, às 9), prioridade (!alta, !!!, urgente) e #categoria.");
        hint.getStyleClass().add("kpi-title");
        hint.setWrapText(true);

        VBox box = new VBox(8, inputRow, capturePreview, hint);
        return UIHelper.createCardSection("⚡ Captura inteligente", box);
    }

    private VBox buildMetricsCard() {
        FlowPane grid = new FlowPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.getChildren().addAll(
                UIHelper.createMiniKpi("Vazão (7 dias)",    throughputValue,    "kpi-green"),
                UIHelper.createMiniKpi("Sequência (dias)",  streakValue,        "kpi-orange"),
                UIHelper.createMiniKpi("Concluídas / semana", completedWeekValue, "kpi-blue"),
                UIHelper.createMiniKpi("Concluídas / mês",  completedMonthValue, "kpi-indigo"),
                UIHelper.createMiniKpi("Abertas",           openValue,          "kpi-cyan"),
                UIHelper.createMiniKpi("Atrasadas",         overdueValue,       "kpi-red"));
        return UIHelper.createCardSection("📊 Indicadores de produtividade", grid);
    }

    private VBox buildFocusCard() {
        focusTitle.getStyleClass().add("section-title");
        focusTitle.setWrapText(true);
        focusReason.getStyleClass().add("kpi-title");
        focusReason.setWrapText(true);

        focusOpenBtn.getStyleClass().add("secondary-button");
        focusOpenBtn.setDisable(true);
        focusOpenBtn.setOnAction(e -> {
            if (focusTaskId != null && taskNavigator != null) {
                taskNavigator.accept(focusDate != null ? focusDate : LocalDate.now(), focusTaskId);
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, new VBox(4, focusTitle, focusReason), spacer, focusOpenBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        return UIHelper.createCardSection("🎯 1 tarefa principal do dia", header);
    }

    private ListView<PriorityItem> buildPriorityList() {
        ListView<PriorityItem> list = new ListView<>(priorityItems);
        list.getStyleClass().add("clean-list");
        list.setPrefHeight(220);
        list.setPlaceholder(new Label("Nenhuma tarefa aberta. 🎉"));
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(PriorityItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                Label title = new Label(item.title());
                title.getStyleClass().add("section-title");
                title.setWrapText(true);
                title.setStyle("-fx-text-fill: " + item.color() + ";");
                Label reason = new Label(item.reason());
                reason.getStyleClass().add("kpi-title");
                reason.setWrapText(true);
                VBox texts = new VBox(2, title, reason);
                HBox.setHgrow(texts, Priority.ALWAYS);
                Label score = new Label(String.valueOf(item.score()));
                score.getStyleClass().add("kpi-value");
                Tooltip.install(score, new Tooltip("Score de urgência (quanto maior, mais prioritário)"));
                HBox row = new HBox(10, texts, score);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
                setText(null);
            }
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                PriorityItem sel = list.getSelectionModel().getSelectedItem();
                if (sel != null && taskNavigator != null) {
                    taskNavigator.accept(sel.anchor(), sel.taskId());
                }
            }
        });
        return list;
    }

    private ListView<String> buildRecommendationList() {
        ListView<String> list = new ListView<>(recommendationItems);
        list.getStyleClass().add("clean-list");
        list.setPrefHeight(180);
        list.setPlaceholder(new Label("Sem recomendações no momento."));
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                Label lbl = new Label(item);
                lbl.setWrapText(true);
                setGraphic(lbl);
                setText(null);
            }
        });
        return list;
    }

    // ── Ações ──────────────────────────────────────────────────────────────

    private void doCapture() {
        String text = captureField.getText();
        if (text == null || text.isBlank()) {
            ctx.setStatus("Digite algo para capturar (ex.: 'Ligar cliente sexta 15h').");
            return;
        }
        NaturalLanguageTaskParser parser = AppContextHolder.get().naturalLanguageTaskParser();
        NaturalLanguageTaskParser.ParsedTask p = parser.parse(text, LocalDate.now());

        // Bloco de persistência isolado: só uma falha aqui significa "não capturado".
        try {
            AppContextHolder.get().taskService().createTask(
                    p.title(), "", p.date(), p.category(),
                    ScheduleType.SINGLE, null, null,
                    p.startTime(), null, p.priority(), TaskStatus.PENDENTE, null);
        } catch (Exception ex) {
            ctx.setStatus("Não foi possível capturar: " + ex.getMessage());
            return;
        }

        // A tarefa já está persistida — o restante é pós-processamento de UI.
        captureField.clear();
        updatePreview();
        String resumo = "✅ Capturado: \"" + p.title() + "\" — " + DATE_FMT.format(p.date())
                + (p.startTime() != null ? " às " + p.startTime() : "")
                + " · " + p.priority().label()
                + (p.category() != null ? " · #" + p.category() : "");
        try {
            ctx.triggerTasksChanged();
            refresh();
            ctx.setStatus(resumo);
        } catch (Exception ex) {
            // Falha ao atualizar as abas não invalida a captura já concluída.
            ctx.setStatus(resumo + " (atualização das abas falhou: " + ex.getMessage() + ")");
        }
    }

    private void updatePreview() {
        String text = captureField.getText();
        if (text == null || text.isBlank()) {
            capturePreview.setText("A pré-visualização da tarefa aparecerá aqui enquanto você digita.");
            return;
        }
        NaturalLanguageTaskParser parser = AppContextHolder.get().naturalLanguageTaskParser();
        NaturalLanguageTaskParser.ParsedTask p = parser.parse(text, LocalDate.now());
        StringBuilder sb = new StringBuilder("→ ").append(p.title())
                .append("  ·  ").append(DATE_FMT.format(p.date()));
        if (p.startTime() != null) sb.append(" às ").append(p.startTime());
        sb.append("  ·  ").append(p.priority().label());
        if (p.category() != null) sb.append("  ·  #").append(p.category());
        capturePreview.setText(sb.toString());
    }

    // ── Atualização ────────────────────────────────────────────────────────

    /** Recalcula todos os indicadores, foco, prioridades e recomendações. */
    public void refresh() {
        try {
            LocalDate today = LocalDate.now();
            InsightsEngine.Insights ins = AppContextHolder.get().insightsService().currentInsights(today);

            throughputValue.setText(Math.round(ins.throughputRate() * 100) + "%");
            streakValue.setText(String.valueOf(ins.currentStreakDays()));
            completedWeekValue.setText(String.valueOf(ins.completedThisWeek()));
            completedMonthValue.setText(String.valueOf(ins.completedThisMonth()));
            openValue.setText(String.valueOf(ins.openCount()));
            overdueValue.setText(String.valueOf(ins.overdueCount()));

            if (ins.focusOfTheDay().isPresent()) {
                var f = ins.focusOfTheDay().get();
                focusTitle.setText(f.title());
                focusReason.setText(AppContextHolder.get().taskPrioritizer().reason(f, today));
                focusTitle.setStyle("-fx-text-fill: " + safeColor(f.priority()) + ";");
                focusTaskId = f.id();
                focusDate = f.effectiveEndDate();
                focusOpenBtn.setDisable(taskNavigator == null);
            } else {
                focusTitle.setText("Nenhuma tarefa pendente 🎉");
                focusReason.setText("Aproveite para capturar novas ideias ou avançar em um projeto.");
                focusTitle.setStyle("");
                focusTaskId = null;
                focusDate = null;
                focusOpenBtn.setDisable(true);
            }

            List<TaskPrioritizer.ScoredTask> top =
                    AppContextHolder.get().insightsService().topPriorities(8, today);
            priorityItems.setAll(top.stream().map(s -> new PriorityItem(
                    s.task().id(),
                    s.task().effectiveEndDate(),
                    s.task().title(),
                    s.reason(),
                    (int) Math.round(s.score()),
                    safeColor(s.task().priority()))).toList());

            recommendationItems.setAll(ins.recommendations());
        } catch (Exception ex) {
            ctx.setStatus("Falha ao atualizar Inteligência: " + ex.getMessage());
        }
    }

    private static String safeColor(TaskPriority p) {
        return p != null ? p.color() : TaskPriority.NORMAL.color();
    }
}
