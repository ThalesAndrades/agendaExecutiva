package com.pessoal.agenda.service;

import com.pessoal.agenda.model.TaskPriority;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class NaturalLanguageTaskParserTest {

    private final NaturalLanguageTaskParser parser = new NaturalLanguageTaskParser();
    // 2026-07-17 é uma sexta-feira — usada como âncora determinística.
    private final LocalDate today = LocalDate.of(2026, 7, 17);

    @Test
    void reconheceHojeAmanhaEDepois() {
        assertEquals(today, parser.parse("Ligar cliente hoje", today).date());
        assertEquals(today.plusDays(1), parser.parse("Comprar pão amanhã", today).date());
        assertEquals(today.plusDays(2), parser.parse("Enviar relatório depois de amanhã", today).date());
    }

    @Test
    void limpaOTituloRemovendoTokens() {
        var p = parser.parse("Pagar aluguel amanhã às 14h #financeiro !alta", today);
        assertEquals("Pagar aluguel", p.title());
        assertEquals(today.plusDays(1), p.date());
        assertEquals("14:00", p.startTime());
        assertEquals(TaskPriority.ALTA, p.priority());
        assertEquals("Financeiro", p.category());
    }

    @Test
    void reconheceHorariosVariados() {
        assertEquals("15:30", parser.parse("Reunião 15:30", today).startTime());
        assertEquals("15:30", parser.parse("Reunião 15h30", today).startTime());
        assertEquals("09:00", parser.parse("Reunião às 9", today).startTime());
        assertEquals("18:00", parser.parse("Academia 18h", today).startTime());
        assertEquals("12:00", parser.parse("Almoço meio-dia", today).startTime());
        assertEquals("00:00", parser.parse("Dormir meia-noite", today).startTime());
    }

    @Test
    void reconhecePrioridades() {
        assertEquals(TaskPriority.CRITICA, parser.parse("Corrigir bug urgente", today).priority());
        assertEquals(TaskPriority.CRITICA, parser.parse("Entregar tudo !!!", today).priority());
        assertEquals(TaskPriority.ALTA, parser.parse("Revisar contrato !!", today).priority());
        assertEquals(TaskPriority.ALTA, parser.parse("Tarefa importante", today).priority());
        assertEquals(TaskPriority.BAIXA, parser.parse("Organizar fotos quando puder", today).priority());
        assertEquals(TaskPriority.NORMAL, parser.parse("Tarefa comum", today).priority());
    }

    @Test
    void reconheceDiaDaSemanaComoProximaOcorrencia() {
        var seg = parser.parse("Reunião segunda", today);
        assertEquals(DayOfWeek.MONDAY, seg.date().getDayOfWeek());
        assertTrue(seg.date().isAfter(today));

        // hoje é sexta: "sexta" deve cair na próxima sexta (nunca hoje)
        var sex = parser.parse("Fechar semana sexta", today);
        assertEquals(DayOfWeek.FRIDAY, sex.date().getDayOfWeek());
        assertEquals(today.plusDays(7), sex.date());
    }

    @Test
    void reconheceEmNDiasEProximaSemana() {
        assertEquals(today.plusDays(3), parser.parse("Estudar em 3 dias", today).date());
        assertEquals(today.plusDays(7), parser.parse("Planejar próxima semana", today).date());
    }

    @Test
    void reconheceDataNumericaEDiaDoMes() {
        var ddmm = parser.parse("Consulta 20/08", today);
        assertEquals(LocalDate.of(2026, 8, 20), ddmm.date());

        var full = parser.parse("Viagem 05/01/2027", today);
        assertEquals(LocalDate.of(2027, 1, 5), full.date());

        // "dia 25" — ainda no mês corrente (25 > 17)
        assertEquals(LocalDate.of(2026, 7, 25), parser.parse("Feira dia 25", today).date());
    }

    @Test
    void dataNumericaPassadaRolaParaProximoAno() {
        // 10/03 já passou em relação a 17/07/2026 → deve ir para 2027
        assertEquals(LocalDate.of(2027, 3, 10), parser.parse("Aniversário 10/03", today).date());
    }

    @Test
    void semDataUsaHojePorPadrao() {
        var p = parser.parse("Tarefa sem data", today);
        assertEquals(today, p.date());
        assertEquals("Tarefa sem data", p.title());
    }

    @Test
    void tituloVazioCaiParaEntradaOriginal() {
        var p = parser.parse("amanhã", today);
        assertFalse(p.title().isBlank());
    }

    @Test
    void entradaNulaNaoQuebra() {
        var p = parser.parse(null, today);
        assertNotNull(p);
        assertEquals(today, p.date());
    }
}
