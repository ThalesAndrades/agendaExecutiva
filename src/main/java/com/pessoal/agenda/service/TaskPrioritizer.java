package com.pessoal.agenda.service;

import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TaskPriority;
import com.pessoal.agenda.model.TaskStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Motor de priorização inteligente de tarefas.
 *
 * Combina, em um único "score de urgência", os fatores que realmente importam
 * para decidir o que fazer primeiro — inspirado na Matriz de Eisenhower e
 * ajustado para apoio a TDAH (evitar que itens antigos sejam esquecidos e
 * incentivar a conclusão do que já foi iniciado):
 *
 * <ul>
 *   <li><b>Prioridade</b> declarada (Crítica/Alta/Normal/Baixa)</li>
 *   <li><b>Proximidade do prazo</b> (atrasada &gt; hoje &gt; amanhã &gt; próxima)</li>
 *   <li><b>Persistência do atraso</b> (quanto mais antiga a pendência, maior o empurrão)</li>
 *   <li><b>Momentum</b> (tarefas EM_ANDAMENTO ganham bônus para serem finalizadas)</li>
 * </ul>
 *
 * Classe pura, sem dependência de UI/banco — facilmente testável.
 */
public class TaskPrioritizer {

    /** Tarefa acompanhada do seu score e de uma explicação legível do porquê. */
    public record ScoredTask(Task task, double score, String reason) {}

    // Pesos base por prioridade declarada.
    private static double priorityWeight(TaskPriority p) {
        if (p == null) return 40;
        return switch (p) {
            case CRITICA -> 100;
            case ALTA    -> 70;
            case NORMAL  -> 40;
            case BAIXA   -> 20;
        };
    }

    /** Score final de urgência de uma tarefa em relação a {@code today}. */
    public double score(Task t, LocalDate today) {
        double base = priorityWeight(t.priority());

        LocalDate deadline = t.effectiveEndDate();
        long daysToDeadline = ChronoUnit.DAYS.between(today, deadline);

        double urgency;
        if (daysToDeadline < 0) {
            // Atrasada: base alta + empurrão crescente (limitado) conforme envelhece.
            long overdue = -daysToDeadline;
            urgency = 60 + Math.min(overdue, 30);
        } else if (daysToDeadline == 0) {
            urgency = 50;                 // vence hoje
        } else if (daysToDeadline == 1) {
            urgency = 30;                 // vence amanhã
        } else if (daysToDeadline <= 3) {
            urgency = 20;
        } else if (daysToDeadline <= 7) {
            urgency = 10;
        } else {
            urgency = Math.max(0, 6 - daysToDeadline / 7.0);
        }

        double momentum = (t.status() == TaskStatus.EM_ANDAMENTO) ? 15 : 0;

        return base + urgency + momentum;
    }

    /** Explicação curta e legível do fator dominante de urgência. */
    public String reason(Task t, LocalDate today) {
        LocalDate deadline = t.effectiveEndDate();
        long days = ChronoUnit.DAYS.between(today, deadline);
        StringBuilder sb = new StringBuilder();
        if (days < 0)        sb.append("Atrasada há ").append(-days).append(" dia(s)");
        else if (days == 0)  sb.append("Vence hoje");
        else if (days == 1)  sb.append("Vence amanhã");
        else                 sb.append("Vence em ").append(days).append(" dia(s)");
        if (t.priority() == TaskPriority.CRITICA || t.priority() == TaskPriority.ALTA)
            sb.append(" · prioridade ").append(t.priority().label().toLowerCase());
        if (t.status() == TaskStatus.EM_ANDAMENTO)
            sb.append(" · já iniciada");
        return sb.toString();
    }

    /** Considera "aberta" toda tarefa não concluída e não cancelada. */
    public static boolean isOpen(Task t) {
        return !t.done()
                && t.status() != TaskStatus.CONCLUIDA
                && t.status() != TaskStatus.CANCELADA;
    }

    /**
     * Ordena as tarefas abertas por urgência (maior primeiro), anexando score e motivo.
     */
    public List<ScoredTask> rank(List<Task> tasks, LocalDate today) {
        List<ScoredTask> scored = new ArrayList<>();
        for (Task t : tasks) {
            if (!isOpen(t)) continue;
            scored.add(new ScoredTask(t, score(t, today), reason(t, today)));
        }
        scored.sort(Comparator.comparingDouble(ScoredTask::score).reversed()
                .thenComparing(s -> s.task().effectiveEndDate()));
        return scored;
    }

    /**
     * Sugere a "1 tarefa principal do dia": a de maior urgência entre as que já
     * estão ativas hoje ou atrasadas. Se não houver nenhuma vencendo/atrasada,
     * cai para a de maior score geral.
     */
    public Optional<Task> focusOfTheDay(List<Task> tasks, LocalDate today) {
        List<ScoredTask> ranked = rank(tasks, today);
        if (ranked.isEmpty()) return Optional.empty();
        return ranked.stream()
                .filter(s -> {
                    LocalDate d = s.task().effectiveEndDate();
                    return !d.isAfter(today); // vence hoje ou já atrasada
                })
                .map(ScoredTask::task)
                .findFirst()
                .or(() -> Optional.of(ranked.get(0).task()));
    }
}
