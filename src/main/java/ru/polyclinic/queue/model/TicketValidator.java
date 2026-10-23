package ru.polyclinic.queue.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Единый набор правил корректности талона. Живёт в модели, потому что
 * проверку вызывают и GUI (диалог ошибок), и CSV-загрузчик (битые строки),
 * и сервис перед вставкой в очередь — правила не должны разъезжаться.
 *
 * <p>Возвращает список нарушений (пустой = корректно), а не бросает
 * исключение: загрузчик CSV собирает ошибки построчно в предупреждения.
 */
public final class TicketValidator {

    /** ФИО: 2–3 слова, каждое с заглавной буквы, допускаются дефис и апостроф. */
    private static final Pattern FULL_NAME_PATTERN =
            Pattern.compile("^[А-ЯЁA-Z][а-яёa-z]*([-'][А-ЯЁA-Z][а-яёa-z]*)?"
                    + "( [А-ЯЁA-Z][а-яёa-z]*([-'][А-ЯЁA-Z][а-яёa-z]*)?){1,2}$");

    /** Кабинет: номер, опционально одна буква (104, 104а, 104-а). */
    private static final Pattern OFFICE_PATTERN =
            Pattern.compile("^\\d{1,4}(-?[а-яёА-ЯЁa-zA-Z])?$");

    private TicketValidator() {
    }

    public static List<String> validate(Ticket ticket) {
        List<String> errors = new ArrayList<>();

        String card = ticket.getCardNumber();
        if (card.isBlank()) {
            errors.add("Номер карты обязателен");
        } else if (!card.matches("\\d{4,20}")) {
            errors.add("Номер карты должен содержать от 4 до 20 цифр");
        }

        String name = ticket.getFullName();
        if (name.isBlank()) {
            errors.add("ФИО обязательно");
        } else if (containsLineBreak(name)) {
            errors.add("ФИО должно занимать одну строку");
        } else if (!FULL_NAME_PATTERN.matcher(name).matches()) {
            errors.add("ФИО должно содержать 2–3 слова, каждое с заглавной буквы");
        }

        String office = ticket.getOffice();
        if (office.isBlank()) {
            errors.add("Кабинет обязателен");
        } else if (containsLineBreak(office)) {
            errors.add("Кабинет должен занимать одну строку");
        } else if (!OFFICE_PATTERN.matcher(office).matches()) {
            errors.add("Кабинет должен быть числом с возможной буквой (например, 104 или 104а)");
        }

        int urgency = ticket.getUrgency();
        if (urgency < 0 || urgency > 3) {
            errors.add("Срочность должна быть от 0 до 3");
        }

        if (ticket.getTakenAt() == null) {
            errors.add("Время взятия обязательно");
        }

        return List.copyOf(errors);
    }

    public static boolean containsLineBreak(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }
}