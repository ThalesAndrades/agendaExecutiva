package com.pessoal.agenda.service;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.ScheduleType;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.model.TaskPriority;
import com.pessoal.agenda.model.TaskStatus;
import com.pessoal.agenda.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração ponta-a-ponta: exercita migrações reais, repositório e o
 * serviço de inteligência sobre um banco SQLite temporário — sem tocar no banco
 * do usuário. Prova que toda a pilha (schema → repo → engine) funciona junta.
 */
class InsightsServiceIntegrationTest {

    private TaskRepository repo;
    private InsightsService service;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        Database db = new Database("jdbc:sqlite:" + tmp.resolve("agenda-test.db"));
        db.runMigrations();
        repo = new TaskRepository(db);
        service = new InsightsService(repo);
    }

    private void save(String title, LocalDate due, TaskPriority p) {
        repo.save(title, "", due, "Geral", ScheduleType.SINGLE, null, null,
                null, null, p, TaskStatus.PENDENTE, null);
    }

    @Test
    void migracaoCriaColunaCompletedAtEContaConclusoes() {
        save("A atrasada", today.minusDays(1), TaskPriority.CRITICA);
        save("B hoje", today, TaskPriority.ALTA);
        save("C futura", today.plusDays(2), TaskPriority.NORMAL);
        save("D concluir", today, TaskPriority.NORMAL);

        long dId = repo.findAll().stream()
                .filter(t -> t.title().equals("D concluir"))
                .mapToLong(Task::id).findFirst().orElseThrow();
        repo.markDone(dId);   // grava completed_at = agora

        // completions inclui hoje com contagem 1
        Map<LocalDate, Integer> comp = repo.completionCountsSince(today.minusDays(7));
        assertEquals(1, comp.getOrDefault(today, 0));

        var ins = service.currentInsights(today);
        assertEquals(3, ins.openCount());            // A, B, C (D concluída)
        assertTrue(ins.overdueCount() >= 1);         // A
        assertTrue(ins.dueTodayCount() >= 1);        // B
        assertEquals(1, ins.completedThisWeek());
        assertTrue(ins.currentStreakDays() >= 1);
        assertTrue(ins.focusOfTheDay().isPresent());
    }

    @Test
    void topPrioridadesRetornaAtrasadaCriticaPrimeiro() {
        save("Atrasada crítica", today.minusDays(3), TaskPriority.CRITICA);
        save("Futura baixa", today.plusDays(10), TaskPriority.BAIXA);

        List<TaskPrioritizer.ScoredTask> top = service.topPriorities(5, today);
        assertFalse(top.isEmpty());
        assertEquals("Atrasada crítica", top.get(0).task().title());
    }

    @Test
    void bancoVazioNaoQuebra() {
        var ins = service.currentInsights(today);
        assertEquals(0, ins.openCount());
        assertTrue(service.topPriorities(5, today).isEmpty());
    }
}
