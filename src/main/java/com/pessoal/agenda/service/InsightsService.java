package com.pessoal.agenda.service;

import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.repository.TaskRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Fachada de inteligência da agenda: reúne os dados persistidos e delega o
 * cálculo às classes puras {@link InsightsEngine} e {@link TaskPrioritizer}.
 *
 * Mantém a UI livre de SQL e de lógica analítica.
 */
public class InsightsService {

    /**
     * Janela de histórico considerada para métricas de conclusão/streak.
     * Alinhada à trava de segurança de {@link InsightsEngine#currentStreak} (400 dias)
     * para não truncar sequências longas.
     */
    private static final int HISTORY_DAYS = 400;

    private final TaskRepository taskRepository;
    private final InsightsEngine engine = new InsightsEngine();
    private final TaskPrioritizer prioritizer = new TaskPrioritizer();

    public InsightsService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /** Indicadores consolidados e recomendações para {@code today}. */
    public InsightsEngine.Insights currentInsights(LocalDate today) {
        List<Task> all = taskRepository.findAll();
        Map<LocalDate, Integer> completions =
                taskRepository.completionCountsSince(today.minusDays(HISTORY_DAYS));
        return engine.compute(all, completions, today);
    }

    /** As {@code limit} tarefas abertas mais urgentes, já pontuadas e explicadas. */
    public List<TaskPrioritizer.ScoredTask> topPriorities(int limit, LocalDate today) {
        List<Task> all = taskRepository.findAll();
        List<TaskPrioritizer.ScoredTask> ranked = prioritizer.rank(all, today);
        return ranked.size() > limit ? ranked.subList(0, limit) : ranked;
    }
}
