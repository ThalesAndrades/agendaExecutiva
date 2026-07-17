package com.pessoal.agenda.app;

import com.pessoal.agenda.model.Category;
import com.pessoal.agenda.model.CategoryDomain;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.application.Platform;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Estado compartilhado entre todos os controllers de UI.
 *
 * Concentra:
 *   - listas de categorias (compartilhadas entre múltiplas abas)
 *   - KPI labels do dashboard (atualizados individualmente por cada controller)
 *   - listas de alertas e próximos prazos (consumidos pelo dashboard)
 *   - callbacks para status, atualização do dashboard e alertas
 */
public class SharedContext {

    // ── Callbacks ──────────────────────────────────────────────────────────
    private final Consumer<String> statusSetter;
    private Runnable dashboardRefreshCallback;
    private Runnable alertRefreshCallback;
    private Runnable categoriesRefreshCallback;
    private Runnable tasksChangedCallback;

    // ── Categorias (compartilhadas entre abas) ─────────────────────────────
    public final ObservableList<Category> taskCatList       = FXCollections.observableArrayList();
    public final ObservableList<Category> checklistCatList  = FXCollections.observableArrayList();
    public final ObservableList<Category> studyCatList      = FXCollections.observableArrayList();
    public final ObservableList<Category> studyTypeCatList  = FXCollections.observableArrayList();
    public final ObservableList<Category> ideaCatList       = FXCollections.observableArrayList();

    public final ObservableList<String>   taskCatNames      = FXCollections.observableArrayList();
    public final ObservableList<String>   checklistCatNames = FXCollections.observableArrayList();
    public final ObservableList<String>   studyCatNames     = FXCollections.observableArrayList();
    public final ObservableList<String>   studyTypeCatNames = FXCollections.observableArrayList();
    public final ObservableList<String>   ideaCatNames      = FXCollections.observableArrayList();

    // ── KPI labels do dashboard ────────────────────────────────────────────
    public final Label openTasksValue       = new Label("0");
    public final Label overdueTasksValue    = new Label("0");
    public final Label pendingPaymentsValue = new Label("0");
    public final Label pendingAmountValue   = new Label("R$ 0,00");
    public final Label checklistPendingValue= new Label("0");
    public final Label studyHoursValue      = new Label("0 h");
    public final Label lowStockValue        = new Label("0");
    public final Label ideasInProgressValue = new Label("0");
    
    // ── Indicador de sessão de estudo ativa (aparecerá nas abas quando o diário
    // estiver minimizado e a sessão estiver rodando)
    public final Label activeStudySessionLabel = new Label("");

    // ── Dados do dashboard ─────────────────────────────────────────────────
    public final ObservableList<String> alertItems    = FXCollections.observableArrayList();
    public final ObservableList<String> upcomingItems = FXCollections.observableArrayList();

    // ── Dados TDAH: tarefas de hoje + protocolos vencendo ────────────────────
    public final ObservableList<String> todayTaskItems   = FXCollections.observableArrayList();
    public final ObservableList<String> expiringProtocolItems = FXCollections.observableArrayList();
    public final Label tasksDueCountLabel = new Label("0");
    public final Label protocolsExpiringCountLabel = new Label("0");

    public SharedContext(Consumer<String> statusSetter) {
        this.statusSetter = statusSetter;
    }

    // ── Configuração de callbacks ──────────────────────────────────────────
    public void setDashboardRefreshCallback(Runnable cb)  { this.dashboardRefreshCallback  = cb; }
    public void setAlertRefreshCallback(Runnable cb)      { this.alertRefreshCallback      = cb; }
    public void setCategoriesRefreshCallback(Runnable cb) { this.categoriesRefreshCallback = cb; }
    public void setTasksChangedCallback(Runnable cb)      { this.tasksChangedCallback      = cb; }

    /**
     * Aciona uma atualização completa após uma mudança em tarefas
     * (criação/edição/conclusão), mantendo todas as abas em sincronia.
     */
    public void triggerTasksChanged() {
        if (tasksChangedCallback != null) tasksChangedCallback.run();
    }

    // ── Ações públicas ─────────────────────────────────────────────────────

    /** Exibe uma mensagem na barra de status da aplicação. */
    public void setStatus(String msg) {
        try {
            if (Platform.isFxApplicationThread()) {
                statusSetter.accept(msg);
            } else {
                Platform.runLater(() -> {
                    try { statusSetter.accept(msg); } catch (Throwable ignored) {}
                });
            }
        } catch (Throwable ex) {
            // If Platform is not available for any reason, fallback to direct call
            try { statusSetter.accept(msg); } catch (Throwable ignored) {}
        }
    }


    /** Aciona atualização dos indicadores do dashboard. */
    public void triggerDashboardRefresh() {
        if (dashboardRefreshCallback != null) dashboardRefreshCallback.run();
    }

    /** Aciona atualização de alertas e próximos prazos. */
    public void triggerAlertRefresh() {
        if (alertRefreshCallback != null) alertRefreshCallback.run();
    }

    /** Aciona atualização completa das categorias a partir do banco. */
    public void refreshCategories() {
        var cs = AppContextHolder.get().categoryService();
        updateCatLists(cs.list(CategoryDomain.TASK),       taskCatList,       taskCatNames);
        updateCatLists(cs.list(CategoryDomain.CHECKLIST),  checklistCatList,  checklistCatNames);
        updateCatLists(cs.list(CategoryDomain.STUDY),      studyCatList,      studyCatNames);
        updateCatLists(cs.list(CategoryDomain.STUDY_TYPE), studyTypeCatList,  studyTypeCatNames);
        updateCatLists(cs.list(CategoryDomain.IDEA),       ideaCatList,       ideaCatNames);
    }

    private static void updateCatLists(List<Category> cats,
                                       ObservableList<Category> catList,
                                       ObservableList<String> nameList) {
        catList.setAll(cats);
        nameList.setAll(cats.stream().map(Category::name).collect(Collectors.toList()));
    }
}

