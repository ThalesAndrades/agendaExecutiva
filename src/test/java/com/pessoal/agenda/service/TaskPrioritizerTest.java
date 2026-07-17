package com.pessoal.agenda.service;

import com.pessoal.agenda.model.ScheduleType;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TaskPriority;
import com.pessoal.agenda.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskPrioritizerTest {

    private final TaskPrioritizer prioritizer = new TaskPrioritizer();
    private final LocalDate today = LocalDate.of(2026, 7, 17);

    private Task task(long id, String title, LocalDate due, TaskPriority p, TaskStatus s) {
        return new Task(id, title, "", due, s == TaskStatus.CONCLUIDA, "Geral",
                ScheduleType.SINGLE, null, null, null, null, p, s, null);
    }

    @Test
    void tarefaAtrasadaCriticaVenceOrdenacao() {
        Task overdueCritical = task(1, "Atrasada crítica", today.minusDays(7),
                TaskPriority.CRITICA, TaskStatus.PENDENTE);
        Task dueTodayHigh = task(2, "Hoje alta", today, TaskPriority.ALTA, TaskStatus.PENDENTE);
        Task futureNormal = task(3, "Futura normal", today.plusDays(10),
                TaskPriority.NORMAL, TaskStatus.PENDENTE);

        List<TaskPrioritizer.ScoredTask> ranked =
                prioritizer.rank(List.of(futureNormal, dueTodayHigh, overdueCritical), today);

        assertEquals(3, ranked.size());
        assertEquals(1, ranked.get(0).task().id());   // atrasada crítica primeiro
        assertEquals(3, ranked.get(2).task().id());   // futura normal por último
        assertTrue(ranked.get(0).score() > ranked.get(1).score());
    }

    @Test
    void filtraConcluidasECanceladas() {
        Task done = task(1, "Feita", today, TaskPriority.ALTA, TaskStatus.CONCLUIDA);
        Task cancelled = task(2, "Cancelada", today, TaskPriority.ALTA, TaskStatus.CANCELADA);
        Task open = task(3, "Aberta", today, TaskPriority.ALTA, TaskStatus.PENDENTE);

        assertFalse(TaskPrioritizer.isOpen(done));
        assertFalse(TaskPrioritizer.isOpen(cancelled));
        assertTrue(TaskPrioritizer.isOpen(open));

        List<TaskPrioritizer.ScoredTask> ranked =
                prioritizer.rank(List.of(done, cancelled, open), today);
        assertEquals(1, ranked.size());
        assertEquals(3, ranked.get(0).task().id());
    }

    @Test
    void momentumAumentaScore() {
        Task pending = task(1, "Pendente", today.plusDays(5), TaskPriority.NORMAL, TaskStatus.PENDENTE);
        Task started = task(2, "Iniciada", today.plusDays(5), TaskPriority.NORMAL, TaskStatus.EM_ANDAMENTO);
        assertTrue(prioritizer.score(started, today) > prioritizer.score(pending, today));
    }

    @Test
    void focoDoDiaPrefereAtrasadaOuDeHoje() {
        Task future = task(1, "Futura crítica", today.plusDays(3),
                TaskPriority.CRITICA, TaskStatus.PENDENTE);
        Task overdue = task(2, "Atrasada normal", today.minusDays(1),
                TaskPriority.NORMAL, TaskStatus.PENDENTE);

        var focus = prioritizer.focusOfTheDay(List.of(future, overdue), today);
        assertTrue(focus.isPresent());
        assertEquals(2, focus.get().id());  // a atrasada vence, mesmo com prioridade menor
    }

    @Test
    void focoDoDiaCaiParaMaiorScoreQuandoNadaVenceHoje() {
        Task a = task(1, "Futura normal", today.plusDays(5), TaskPriority.NORMAL, TaskStatus.PENDENTE);
        Task b = task(2, "Futura crítica", today.plusDays(5), TaskPriority.CRITICA, TaskStatus.PENDENTE);
        var focus = prioritizer.focusOfTheDay(List.of(a, b), today);
        assertTrue(focus.isPresent());
        assertEquals(2, focus.get().id());
    }

    @Test
    void listaVaziaNaoTemFoco() {
        assertTrue(prioritizer.focusOfTheDay(List.of(), today).isEmpty());
        assertTrue(prioritizer.rank(List.of(), today).isEmpty());
    }
}
