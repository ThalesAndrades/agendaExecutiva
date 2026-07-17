package com.pessoal.agenda.service;

import com.pessoal.agenda.model.Task;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Núcleo analítico da aba "Inteligência".
 *
 * Recebe os dados brutos (tarefas + conclusões por dia) e produz um conjunto de
 * indicadores e recomendações acionáveis. É uma classe <b>pura</b>: não acessa
 * banco nem UI, o que a torna trivialmente testável com entradas controladas.
 */
public class InsightsEngine {

    /** Fotografia consolidada da produtividade em um determinado dia. */
    public record Insights(
            int openCount,
            int overdueCount,
            int dueTodayCount,
            int completedThisWeek,
            int completedThisMonth,
            double throughputRate,       // 0..1 — vazão dos últimos 7 dias
            int currentStreakDays,       // dias consecutivos concluindo tarefas
            String busiestCategory,      // categoria com mais tarefas abertas (ou null)
            Optional<Task> focusOfTheDay,
            List<String> recommendations
    ) {}

    private final TaskPrioritizer prioritizer = new TaskPrioritizer();

    /**
     * @param allTasks          todas as tarefas conhecidas (abertas e concluídas)
     * @param completionsByDate mapa data → nº de tarefas concluídas naquele dia
     * @param today             data de referência
     */
    public Insights compute(List<Task> allTasks,
                            Map<LocalDate, Integer> completionsByDate,
                            LocalDate today) {
        if (allTasks == null) allTasks = List.of();
        if (completionsByDate == null) completionsByDate = Map.of();

        int open = 0, overdue = 0, dueToday = 0;
        Map<String, Integer> openByCategory = new LinkedHashMap<>();

        for (Task t : allTasks) {
            if (!TaskPrioritizer.isOpen(t)) continue;
            open++;
            if (t.effectiveEndDate().isBefore(today)) overdue++;
            if (t.isActiveOn(today)) dueToday++;
            String cat = (t.category() == null || t.category().isBlank()) ? "Geral" : t.category();
            openByCategory.merge(cat, 1, Integer::sum);
        }

        int completedWeek = sumRange(completionsByDate, today.minusDays(6), today);
        int completedMonth = sumRange(completionsByDate,
                today.withDayOfMonth(1), today);

        // Vazão: quanto do "trabalho recente" (concluído + ainda atrasado) foi concluído.
        double throughput;
        int denom = completedWeek + overdue;
        throughput = denom == 0 ? 1.0 : (double) completedWeek / denom;

        int streak = currentStreak(completionsByDate, today);
        String busiest = openByCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        Optional<Task> focus = prioritizer.focusOfTheDay(allTasks, today);

        List<String> recs = buildRecommendations(
                open, overdue, dueToday, completedWeek, streak, busiest, focus, today);

        return new Insights(open, overdue, dueToday, completedWeek, completedMonth,
                throughput, streak, busiest, focus, recs);
    }

    // ── Recomendações baseadas em regras ─────────────────────────────────────

    private List<String> buildRecommendations(int open, int overdue, int dueToday,
                                              int completedWeek, int streak,
                                              String busiest, Optional<Task> focus,
                                              LocalDate today) {
        List<String> recs = new ArrayList<>();

        focus.ifPresent(f -> recs.add("🎯 Foco sugerido para hoje: \"" + f.title() + "\"."));

        if (overdue > 0) {
            recs.add("⚠️ Você tem " + overdue + " tarefa(s) atrasada(s). "
                    + "Resolver a mais prioritária primeiro reduz a bola de neve.");
        } else if (dueToday == 0 && open > 0) {
            recs.add("✅ Nada vence hoje — bom momento para adiantar uma pendência futura.");
        } else if (open == 0) {
            recs.add("🎉 Sem tarefas em aberto. Que tal capturar uma nova ideia ou projeto?");
        }

        if (streak >= 3) {
            recs.add("🔥 Sequência de " + streak + " dias concluindo tarefas. Mantenha o ritmo!");
        } else if (streak == 0 && completedWeek == 0 && open > 0) {
            recs.add("🌱 Nenhuma tarefa concluída recentemente. Comece por uma pequena agora para destravar.");
        }

        if (dueToday > 0) {
            recs.add("📋 " + dueToday + " tarefa(s) ativa(s) para hoje. Divida em passos curtos se travar.");
        }

        if (busiest != null && open >= 3) {
            recs.add("🗂 Categoria com mais tarefas abertas: \"" + busiest + "\". "
                    + "Um bloco dedicado a ela pode render mais.");
        }

        if (recs.isEmpty()) {
            recs.add("👍 Tudo sob controle. Continue registrando o que aparecer para não esquecer.");
        }
        return recs;
    }

    // ── Cálculos auxiliares ──────────────────────────────────────────────────

    private static int sumRange(Map<LocalDate, Integer> byDate, LocalDate from, LocalDate to) {
        int total = 0;
        for (Map.Entry<LocalDate, Integer> e : byDate.entrySet()) {
            LocalDate d = e.getKey();
            if (d != null && !d.isBefore(from) && !d.isAfter(to)) {
                total += e.getValue() == null ? 0 : e.getValue();
            }
        }
        return total;
    }

    /**
     * Conta dias consecutivos com pelo menos uma conclusão terminando em hoje.
     * Se hoje ainda não teve conclusões, a sequência é contada a partir de ontem
     * (o dia corrente não "quebra" o streak enquanto está em andamento).
     */
    static int currentStreak(Map<LocalDate, Integer> byDate, LocalDate today) {
        if (byDate == null || byDate.isEmpty()) return 0;
        LocalDate cursor = today;
        if (countOn(byDate, today) == 0) {
            cursor = today.minusDays(1);
        }
        int streak = 0;
        while (countOn(byDate, cursor) > 0) {
            streak++;
            cursor = cursor.minusDays(1);
            if (ChronoUnit.DAYS.between(cursor, today) > 400) break; // trava de segurança
        }
        return streak;
    }

    private static int countOn(Map<LocalDate, Integer> byDate, LocalDate d) {
        Integer v = byDate.get(d);
        return v == null ? 0 : v;
    }
}
