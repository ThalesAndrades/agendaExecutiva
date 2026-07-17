package com.pessoal.agenda.service;

import com.pessoal.agenda.model.TaskPriority;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpretador de "captura rápida inteligente" em português.
 *
 * Converte uma frase livre digitada pelo usuário em campos estruturados de
 * tarefa (título, data, horário, prioridade e categoria), reconhecendo
 * expressões naturais como:
 *
 * <pre>
 *   "Pagar aluguel amanhã às 14h #financeiro !alta"
 *   "Ligar para o cliente sexta 15:30"
 *   "Revisar artigo dia 20 !!!"
 *   "Comprar remédio hoje #saude"
 *   "Enviar relatório 15/03 urgente"
 *   "Estudar estatística em 3 dias"
 * </pre>
 *
 * É totalmente determinístico e sem dependência de UI ou banco — projetado
 * para ser exercitado por testes unitários com uma data-base fixa.
 */
public class NaturalLanguageTaskParser {

    /** Resultado estruturado da interpretação. Campos podem ser nulos quando ausentes. */
    public record ParsedTask(String title, LocalDate date, String startTime,
                             TaskPriority priority, String category) {}

    // ── Padrões ─────────────────────────────────────────────────────────────
    // UNICODE_CHARACTER_CLASS torna \b, \w e \d cientes de acentuação — essencial
    // para reconhecer fronteiras de palavra em "amanhã", "às", "sábado", etc.
    private static final int FLAGS =
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern CATEGORY = Pattern.compile("#([\\p{L}][\\p{L}0-9_-]*)", FLAGS);

    // Prioridade — o primeiro grupo encontrado define a prioridade.
    private static final Pattern P_CRITICA = Pattern.compile(
            "(!!!|!critica|!crítica|\\burgent[ií]ssim[ao]\\b|\\burgente\\b|\\bcr[ií]tic[ao]\\b)", FLAGS);
    private static final Pattern P_ALTA = Pattern.compile(
            "(!!|!alta|\\bimportante\\b|prioridade alta)", FLAGS);
    private static final Pattern P_BAIXA = Pattern.compile(
            "(!baixa|\\bsem pressa\\b|\\bquando puder\\b)", FLAGS);

    // Horário
    private static final Pattern T_AS = Pattern.compile(
            "\\b(?:às|as|ás)\\s*(\\d{1,2})(?:[:h](\\d{2}))?\\s*h?\\b", FLAGS);
    private static final Pattern T_HHMM = Pattern.compile("\\b(\\d{1,2})[:h](\\d{2})\\b", FLAGS);
    private static final Pattern T_HH = Pattern.compile("\\b(\\d{1,2})\\s*h\\b", FLAGS);
    private static final Pattern T_MEIODIA = Pattern.compile("\\bmeio[-\\s]?dia\\b", FLAGS);
    private static final Pattern T_MEIANOITE = Pattern.compile("\\bmeia[-\\s]?noite\\b", FLAGS);

    // Data
    private static final Pattern D_DEPOIS_AMANHA = Pattern.compile("\\bdepois de amanh[ãa]\\b", FLAGS);
    private static final Pattern D_AMANHA = Pattern.compile("\\bamanh[ãa]\\b", FLAGS);
    private static final Pattern D_HOJE = Pattern.compile("\\bhoje\\b", FLAGS);
    private static final Pattern D_ONTEM = Pattern.compile("\\bontem\\b", FLAGS);
    private static final Pattern D_PROX_SEMANA = Pattern.compile(
            "\\b(?:pr[óo]xima semana|semana que vem)\\b", FLAGS);
    private static final Pattern D_EM_N_DIAS = Pattern.compile(
            "\\b(?:daqui a|daqui à|em)\\s+(\\d{1,3})\\s*dias?\\b", FLAGS);
    private static final Pattern D_WEEKDAY = Pattern.compile(
            "\\b(?:pr[óo]xim[ao]\\s+)?(segunda|ter[çc]a|quarta|quinta|sexta|s[áa]bado|domingo)(?:-feira|\\s+feira)?\\b",
            FLAGS);
    private static final Pattern D_DDMM = Pattern.compile(
            "\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b", FLAGS);
    private static final Pattern D_DIA_N = Pattern.compile("\\bdia\\s+(\\d{1,2})\\b", FLAGS);

    // ── API ─────────────────────────────────────────────────────────────────

    public ParsedTask parse(String input) {
        return parse(input, LocalDate.now());
    }

    /** Interpreta {@code input} usando {@code today} como referência para datas relativas. */
    public ParsedTask parse(String input, LocalDate today) {
        if (input == null) input = "";
        String work = input.trim();

        // 1) Categoria (#tag)
        String category = null;
        Matcher cm = CATEGORY.matcher(work);
        if (cm.find()) {
            category = capitalize(cm.group(1));
            work = removeRange(work, cm.start(), cm.end());
        }

        // 2) Prioridade
        TaskPriority priority = TaskPriority.NORMAL;
        String[] holder = { work };
        if (matchAndStrip(P_CRITICA, holder))      priority = TaskPriority.CRITICA;
        else if (matchAndStrip(P_ALTA, holder))    priority = TaskPriority.ALTA;
        else if (matchAndStrip(P_BAIXA, holder))   priority = TaskPriority.BAIXA;
        work = holder[0];

        // 3) Horário
        String time = null;
        Matcher tm;
        if ((tm = T_AS.matcher(work)).find()) {
            time = buildTime(tm.group(1), tm.group(2));
            if (time != null) work = removeRange(work, tm.start(), tm.end());
        }
        if (time == null && (tm = T_HHMM.matcher(work)).find()) {
            time = buildTime(tm.group(1), tm.group(2));
            if (time != null) work = removeRange(work, tm.start(), tm.end());
        }
        if (time == null && (tm = T_HH.matcher(work)).find()) {
            time = buildTime(tm.group(1), "00");
            if (time != null) work = removeRange(work, tm.start(), tm.end());
        }
        if (time == null && (tm = T_MEIODIA.matcher(work)).find()) {
            time = "12:00"; work = removeRange(work, tm.start(), tm.end());
        }
        if (time == null && (tm = T_MEIANOITE.matcher(work)).find()) {
            time = "00:00"; work = removeRange(work, tm.start(), tm.end());
        }

        // 4) Data (a primeira expressão reconhecida vence)
        LocalDate date = today;
        Matcher dm;
        if ((dm = D_DEPOIS_AMANHA.matcher(work)).find()) {
            date = today.plusDays(2); work = removeRange(work, dm.start(), dm.end());
        } else if ((dm = D_AMANHA.matcher(work)).find()) {
            date = today.plusDays(1); work = removeRange(work, dm.start(), dm.end());
        } else if ((dm = D_HOJE.matcher(work)).find()) {
            date = today; work = removeRange(work, dm.start(), dm.end());
        } else if ((dm = D_ONTEM.matcher(work)).find()) {
            date = today.minusDays(1); work = removeRange(work, dm.start(), dm.end());
        } else if ((dm = D_PROX_SEMANA.matcher(work)).find()) {
            date = today.plusDays(7); work = removeRange(work, dm.start(), dm.end());
        } else if ((dm = D_EM_N_DIAS.matcher(work)).find()) {
            int n = safeInt(dm.group(1), 0);
            date = today.plusDays(Math.max(0, n)); work = removeRange(work, dm.start(), dm.end());
        } else if ((dm = D_WEEKDAY.matcher(work)).find()) {
            date = nextWeekday(today, dm.group(1)); work = removeRange(work, dm.start(), dm.end());
        } else if ((dm = D_DDMM.matcher(work)).find()) {
            LocalDate parsed = parseDayMonth(dm.group(1), dm.group(2), dm.group(3), today);
            if (parsed != null) { date = parsed; work = removeRange(work, dm.start(), dm.end()); }
        } else if ((dm = D_DIA_N.matcher(work)).find()) {
            LocalDate parsed = parseDayOfMonth(dm.group(1), today);
            if (parsed != null) { date = parsed; work = removeRange(work, dm.start(), dm.end()); }
        }

        // 5) Título limpo
        String title = normalizeWhitespace(work);
        if (title.isBlank()) title = normalizeWhitespace(input);
        return new ParsedTask(title, date, time, priority, category);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean matchAndStrip(Pattern p, String[] holder) {
        Matcher m = p.matcher(holder[0]);
        if (m.find()) {
            holder[0] = removeRange(holder[0], m.start(), m.end());
            return true;
        }
        return false;
    }

    private static String buildTime(String h, String mm) {
        int hour = safeInt(h, -1);
        int min = safeInt(mm, 0);
        if (hour < 0 || hour > 23 || min < 0 || min > 59) return null;
        return String.format("%02d:%02d", hour, min);
    }

    private static LocalDate nextWeekday(LocalDate today, String name) {
        int target = weekdayValue(name);
        if (target < 0) return today;
        int diff = (target - today.getDayOfWeek().getValue() + 7) % 7;
        if (diff == 0) diff = 7; // sempre a próxima ocorrência (nunca hoje)
        return today.plusDays(diff);
    }

    /** ISO: segunda=1 ... domingo=7. Aceita variações com/sem acento. */
    private static int weekdayValue(String name) {
        String n = stripAccents(name.toLowerCase());
        return switch (n) {
            case "segunda" -> 1;
            case "terca"   -> 2;
            case "quarta"  -> 3;
            case "quinta"  -> 4;
            case "sexta"   -> 5;
            case "sabado"  -> 6;
            case "domingo" -> 7;
            default -> -1;
        };
    }

    private static LocalDate parseDayMonth(String d, String m, String y, LocalDate today) {
        int day = safeInt(d, -1), month = safeInt(m, -1);
        if (day < 1 || day > 31 || month < 1 || month > 12) return null;
        try {
            if (y != null) {
                int year = safeInt(y, today.getYear());
                if (year < 100) year += 2000;
                return LocalDate.of(year, month, day);
            }
            LocalDate candidate = LocalDate.of(today.getYear(), month, day);
            if (candidate.isBefore(today)) candidate = candidate.plusYears(1);
            return candidate;
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate parseDayOfMonth(String d, LocalDate today) {
        int day = safeInt(d, -1);
        if (day < 1 || day > 31) return null;
        YearMonth ym = YearMonth.from(today);
        if (day > ym.lengthOfMonth() || LocalDate.of(ym.getYear(), ym.getMonthValue(), day).isBefore(today)) {
            ym = ym.plusMonths(1);
            if (day > ym.lengthOfMonth()) return null;
        }
        return LocalDate.of(ym.getYear(), ym.getMonthValue(), day);
    }

    private static int safeInt(String s, int fallback) {
        if (s == null) return fallback;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static String removeRange(String s, int start, int end) {
        return (s.substring(0, start) + " " + s.substring(end));
    }

    private static String normalizeWhitespace(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String stripAccents(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
