package com.pessoal.agenda.service;

import com.pessoal.agenda.model.ScheduleType;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TaskPriority;
import com.pessoal.agenda.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InsightsEngineTest {

    private final InsightsEngine engine = new InsightsEngine();
    private final LocalDate today = LocalDate.of(2026, 7, 17);

    private Task open(long id, LocalDate due, TaskPriority p, String cat) {
        return new Task(id, "T" + id, "", due, false, cat,
                ScheduleType.SINGLE, null, null, null, null, p, TaskStatus.PENDENTE, null);
    }

    @Test
    void contaAbertasAtrasadasEHoje() {
        List<Task> tasks = List.of(
                open(1, today.minusDays(2), TaskPriority.ALTA, "Trabalho"),   // atrasada
                open(2, today, TaskPriority.NORMAL, "Casa"),                  // hoje
                open(3, today.plusDays(5), TaskPriority.BAIXA, "Trabalho"));  // futura

        var ins = engine.compute(tasks, Map.of(), today);
        assertEquals(3, ins.openCount());
        assertEquals(1, ins.overdueCount());
        assertEquals(1, ins.dueTodayCount());
        assertEquals("Trabalho", ins.busiestCategory());
        assertTrue(ins.focusOfTheDay().isPresent());
        assertFalse(ins.recommendations().isEmpty());
    }

    @Test
    void somaConclusoesDaSemanaEDoMes() {
        Map<LocalDate, Integer> comp = new HashMap<>();
        comp.put(today, 2);
        comp.put(today.minusDays(3), 1);   // dentro da semana
        comp.put(today.minusDays(10), 5);  // fora da semana, dentro do mês (julho)
        comp.put(today.minusDays(40), 9);  // fora do mês

        var ins = engine.compute(List.of(), comp, today);
        assertEquals(3, ins.completedThisWeek());   // 2 + 1
        assertEquals(8, ins.completedThisMonth());  // 2 + 1 + 5
    }

    @Test
    void taxaDeVazaoConsideraConcluidasEAtrasadas() {
        Map<LocalDate, Integer> comp = new HashMap<>();
        comp.put(today, 4);
        List<Task> tasks = List.of(open(1, today.minusDays(1), TaskPriority.NORMAL, "X")); // 1 atrasada
        var ins = engine.compute(tasks, comp, today);
        // 4 concluídas / (4 + 1 atrasada) = 0.8
        assertEquals(0.8, ins.throughputRate(), 1e-9);
    }

    @Test
    void streakContaDiasConsecutivos() {
        Map<LocalDate, Integer> comp = new HashMap<>();
        comp.put(today, 1);
        comp.put(today.minusDays(1), 2);
        comp.put(today.minusDays(2), 1);
        // lacuna em -3
        comp.put(today.minusDays(4), 1);
        assertEquals(3, InsightsEngine.currentStreak(comp, today));
    }

    @Test
    void streakContaAPartirDeOntemQuandoHojeVazio() {
        Map<LocalDate, Integer> comp = new HashMap<>();
        comp.put(today.minusDays(1), 1);
        comp.put(today.minusDays(2), 1);
        // hoje sem conclusões ainda — não deve zerar o streak
        assertEquals(2, InsightsEngine.currentStreak(comp, today));
    }

    @Test
    void streakZeroSemHistorico() {
        assertEquals(0, InsightsEngine.currentStreak(Map.of(), today));
        assertEquals(0, InsightsEngine.currentStreak(null, today));
    }

    @Test
    void recomendaComecarQuandoNadaConcluido() {
        List<Task> tasks = List.of(open(1, today.plusDays(2), TaskPriority.NORMAL, "X"));
        var ins = engine.compute(tasks, Map.of(), today);
        assertTrue(ins.recommendations().stream()
                .anyMatch(r -> r.toLowerCase().contains("comece") || r.toLowerCase().contains("destravar")
                        || r.toLowerCase().contains("adiantar")));
    }

    @Test
    void semTarefasRecomendaCaptura() {
        var ins = engine.compute(List.of(), Map.of(), today);
        assertEquals(0, ins.openCount());
        assertFalse(ins.recommendations().isEmpty());
    }

    @Test
    void entradasNulasNaoQuebram() {
        var ins = engine.compute(null, null, today);
        assertEquals(0, ins.openCount());
        assertEquals(1.0, ins.throughputRate(), 1e-9);
        assertNotNull(ins.recommendations());
    }
}
