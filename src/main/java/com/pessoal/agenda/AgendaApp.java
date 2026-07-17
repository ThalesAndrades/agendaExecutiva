package com.pessoal.agenda;

import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.service.PendencyNotificationService;
import com.pessoal.agenda.ui.controller.*;
import com.pessoal.agenda.ui.view.ThemeManager;
import com.pessoal.agenda.ui.view.WindowManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.YearMonth;

/**
 * Agenda Científica Pessoal — ponto de entrada da aplicação JavaFX.
 *
 * Esta classe é responsável apenas pela montagem da janela principal
 * e coordenação dos refreshes entre os controllers de cada aba.
 *
 * A lógica de cada módulo está em {@code com.pessoal.agenda.ui.controller}.
 */
public class AgendaApp extends Application {

    // ── Infraestrutura legada (ainda necessária para operações não migradas) ─
    private final DatabaseService databaseService = new DatabaseService();

    // ── Estado compartilhado entre todos os controllers ────────────────────
    private SharedContext ctx;

    // ── Controllers de cada aba ────────────────────────────────────────────
    private DashboardController  dashboardCtrl;
    private AgendaTabController  agendaCtrl;
    private ChecklistController  checklistCtrl;
    private FinanceController    financeCtrl;
    private SalesController      salesCtrl;
    private StudyController      studyCtrl;
    private IdeasController      ideasCtrl;
    private ConfigController     configCtrl;
    private InsightsController   insightsCtrl;

    // ── Barra de status ────────────────────────────────────────────────────
    private final Label statusLabel = new Label("Sistema pronto para uso.");
    private final Label statusAlertBadge = new Label("SEM ALERTAS");
    private final Tooltip statusAlertTooltip = new Tooltip();
    private final ContextMenu statusAlertPopover = new ContextMenu();
    private Timeline statusAlertBlink;
    private int badgeOverdueAlerts = 0;
    private int badgeTodayCount = 0;
    private int badgeProtocolCount = 0;

    // ══════════════════════════════════════════════════════════════════════
    @Override
    public void start(Stage stage) {
        databaseService.initialize();

        // Contexto compartilhado com callback de status
        ctx = new SharedContext(statusLabel::setText);

        // Callbacks coordenados pelo AgendaApp
        ctx.setDashboardRefreshCallback(this::refreshDashboardKpis);
        ctx.setAlertRefreshCallback(this::refreshAlertsAndUpcoming);

        // Inicialização dos controllers
        dashboardCtrl = new DashboardController(ctx, databaseService);
        agendaCtrl    = new AgendaTabController(ctx, databaseService);
        checklistCtrl = new ChecklistController(ctx);
        financeCtrl   = new FinanceController(ctx, databaseService);
        salesCtrl     = new SalesController(ctx, databaseService);
        studyCtrl     = new StudyController(ctx);
        ideasCtrl     = new IdeasController(ctx, databaseService);
        configCtrl    = new ConfigController(ctx);
        insightsCtrl  = new InsightsController(ctx);

        // Após qualquer mudança em tarefas, ressincroniza todas as abas.
        ctx.setTasksChangedCallback(() -> refreshAllData(YearMonth.from(agendaCtrl.getCurrentDate())));

        // Montagem do TabPane
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("main-tabs");

        // Navegação a partir do Dashboard: Agenda=1, Financeiro=3
        dashboardCtrl.setTabNavigator(idx -> tabPane.getSelectionModel().select(idx));
        dashboardCtrl.setTaskNavigator((date, taskId) -> {
            tabPane.getSelectionModel().select(1);
            agendaCtrl.navigateToTask(date, taskId);
        });
        insightsCtrl.setTaskNavigator((date, taskId) -> {
            tabPane.getSelectionModel().select(1);
            agendaCtrl.navigateToTask(date, taskId);
        });

        // A aba Inteligência é adicionada por último para preservar os índices
        // usados pela navegação do Dashboard (Agenda=1, Financeiro=3).
        tabPane.getTabs().addAll(
                dashboardCtrl.buildTab(),
                agendaCtrl.buildTab(),
                checklistCtrl.buildTab(),
                financeCtrl.buildTab(),
                salesCtrl.buildTab(),
                studyCtrl.buildTab(),
                ideasCtrl.buildTab(),
                configCtrl.buildTab(),
                insightsCtrl.buildTab()
        );

        // Layout principal
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(buildHeader(tabPane));
        root.setCenter(tabPane);
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1260, 820);
        ThemeManager.getInstance().applyTo(scene);
        registerShortcut(scene);

        // Hook global: aplica tema automaticamente a TODA nova janela/diálogo que abrir
        ThemeManager.getInstance().initGlobalWindowHook();

        stage.setTitle("Agenda Científica Pessoal — Planejamento Integrado");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            PendencyNotificationService.getInstance().stop();
            WindowManager.closeAll();
            Platform.exit();
        });
        stage.show();

        // Carga inicial de todos os dados
        refreshAllData(YearMonth.now());

        // Lembretes periódicos: a cada 5 minutos, se houver alertas críticos.
        PendencyNotificationService.getInstance().start(5 * 60 * 1000L, () -> {
            refreshAlertsAndUpcoming();
            refreshDashboardKpis();
            updateCriticalBadge();
            ctx.setStatus("Lembrete: você tem pendências críticas para revisar.");
        });
    }

    // ── Coordenação de refreshes ───────────────────────────────────────────

    private void refreshAllData(YearMonth month) {
        ctx.refreshCategories();
        agendaCtrl.refresh();              // agenda + alertas + upcoming
        checklistCtrl.refresh();
        financeCtrl.refresh();
        salesCtrl.refresh();
        studyCtrl.refresh();
        ideasCtrl.refresh();
        insightsCtrl.refresh();
        refreshDashboardKpis();
        refreshAlertsAndUpcoming();
        updateCriticalBadge();
    }

    private void refreshDashboardKpis() {
        YearMonth month = YearMonth.from(agendaCtrl.getCurrentDate());
        dashboardCtrl.refreshKpis(month);
        updateCriticalBadge();
    }

    private void refreshAlertsAndUpcoming() {
        ctx.alertItems.setAll(databaseService.listDeadlineAlerts());
        ctx.upcomingItems.setAll(databaseService.listUpcomingDeadlines(10));
        updateCriticalBadge();
    }

    private void registerShortcut(Scene scene) {
        Runnable remindAction = () -> {
            refreshAlertsAndUpcoming();
            refreshDashboardKpis();
            PendencyNotificationService.getInstance().forceCheck();
            ctx.setStatus("Lembrete manual disparado (atalho Ctrl/Cmd+S ou Ctrl/Cmd+Shift+S).");
        };

        KeyCombination remindNow = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN);
        KeyCombination remindNowAlt = new KeyCodeCombination(
                KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);

        scene.getAccelerators().put(remindNow, remindAction);
        scene.getAccelerators().put(remindNowAlt, remindAction);
    }

    private void updateCriticalBadge() {
        int overdueAlerts = (int) ctx.alertItems.stream()
                .filter(s -> s != null && !s.isBlank() && !s.startsWith("Sem atrasos"))
                .count();
        int todayCount = ctx.todayTaskItems.size();
        int protocolCount = ctx.expiringProtocolItems.size();
        int critical = overdueAlerts + todayCount + protocolCount;

        badgeOverdueAlerts = overdueAlerts;
        badgeTodayCount = todayCount;
        badgeProtocolCount = protocolCount;
        rebuildStatusPopover();

        if (critical <= 0) {
            statusAlertBadge.setText("SEM ALERTAS");
            statusAlertTooltip.setText("Sem pendências críticas no momento.");
            statusAlertBadge.getStyleClass().remove("status-alert-critical");
            statusAlertBadge.getStyleClass().remove("status-alert-warning");
            statusAlertBadge.getStyleClass().add("status-alert-ok");
            statusAlertBadge.setOpacity(1.0);
            stopBadgeBlink();
            return;
        }

        statusAlertBadge.setText("PENDÊNCIAS: " + critical + " (A:" + overdueAlerts
                + " H:" + todayCount + " P:" + protocolCount + ")");
        statusAlertTooltip.setText("Pendências críticas:\n"
                + "A = Alertas de atraso: " + overdueAlerts + "\n"
                + "H = Tarefas de hoje: " + todayCount + "\n"
                + "P = Protocolos periódicos: " + protocolCount + "\n\n"
                + "Dica: use Ctrl/Cmd+S para lembrar agora.");
        statusAlertBadge.getStyleClass().remove("status-alert-ok");
        statusAlertBadge.getStyleClass().remove("status-alert-critical");
        statusAlertBadge.getStyleClass().remove("status-alert-warning");
        if (overdueAlerts > 0) {
            statusAlertBadge.getStyleClass().add("status-alert-critical");
        } else {
            statusAlertBadge.getStyleClass().add("status-alert-warning");
        }
        startBadgeBlink();
    }

    private void startBadgeBlink() {
        if (statusAlertBlink != null && statusAlertBlink.getStatus() == Animation.Status.RUNNING) return;
        statusAlertBlink = new Timeline(
                new KeyFrame(Duration.ZERO, e -> statusAlertBadge.setOpacity(1.0)),
                new KeyFrame(Duration.millis(600), e -> statusAlertBadge.setOpacity(0.45)),
                new KeyFrame(Duration.millis(1200), e -> statusAlertBadge.setOpacity(1.0))
        );
        statusAlertBlink.setCycleCount(Animation.INDEFINITE);
        statusAlertBlink.play();
    }

    private void stopBadgeBlink() {
        if (statusAlertBlink != null) {
            statusAlertBlink.stop();
            statusAlertBlink = null;
        }
    }

    // ── Construção do cabeçalho e barra de status ──────────────────────────

    private Node buildHeader(TabPane tabPane) {
        Label title    = new Label("Painel de Operação Pessoal");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label(
                "Planejamento diário, financeiro, estudos e execução de projetos em um único fluxo");
        subtitle.getStyleClass().add("page-subtitle");

        Button refreshAllBtn = new Button("Atualizar tudo");
        refreshAllBtn.getStyleClass().add("primary-button");
        refreshAllBtn.setOnAction(e -> {
            refreshAllData(YearMonth.from(agendaCtrl.getCurrentDate()));
            ctx.setStatus("Dados atualizados com sucesso.");
        });

        Button focusBtn = new Button("Ir para Dashboard");
        focusBtn.getStyleClass().add("secondary-button");
        focusBtn.setOnAction(e -> tabPane.getSelectionModel().select(0));

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, new VBox(4, title, subtitle), spacer, focusBtn, refreshAllBtn);
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 18, 14, 18));
        return header;
    }

    private Node buildStatusBar() {
        statusLabel.getStyleClass().add("status-label");
        statusAlertBadge.getStyleClass().addAll("status-label", "status-alert-badge", "status-alert-ok");
        statusAlertTooltip.setText("Sem pendências críticas no momento.");
        Tooltip.install(statusAlertBadge, statusAlertTooltip);
        statusAlertBadge.setOnMouseClicked(e -> {
            rebuildStatusPopover();
            if (statusAlertPopover.isShowing()) {
                statusAlertPopover.hide();
            } else {
                statusAlertPopover.show(statusAlertBadge, e.getScreenX(), e.getScreenY());
            }
            e.consume();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(10, statusLabel, spacer, statusAlertBadge);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 18, 8, 18));
        return bar;
    }

    private void rebuildStatusPopover() {
        statusAlertPopover.getItems().clear();
        MenuItem title = new MenuItem("Pendências críticas");
        title.setDisable(true);
        MenuItem overdue = new MenuItem("Atrasos (A): " + badgeOverdueAlerts);
        overdue.setDisable(true);
        MenuItem today = new MenuItem("Hoje (H): " + badgeTodayCount);
        today.setDisable(true);
        MenuItem protocol = new MenuItem("Protocolos (P): " + badgeProtocolCount);
        protocol.setDisable(true);
        MenuItem remindNow = new MenuItem("Lembrar agora (Ctrl/Cmd+S ou Ctrl/Cmd+Shift+S)");
        remindNow.setOnAction(ev -> {
            refreshAlertsAndUpcoming();
            refreshDashboardKpis();
            PendencyNotificationService.getInstance().forceCheck();
            ctx.setStatus("Lembrete manual disparado pelo badge.");
        });
        statusAlertPopover.getItems().addAll(title, new javafx.scene.control.SeparatorMenuItem(), overdue, today, protocol,
                new javafx.scene.control.SeparatorMenuItem(), remindNow);
    }
}

