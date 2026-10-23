package ru.polyclinic.queue.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Единый набор правил корректности талона. Живёт в модели, потому что
 * проверку вызывают и GUI (диалог ошибок), и CSV-загрузчик (битые строки),
 * и сервис перед вставкой в очередь — правила не должны разъезжаться.
 *
 * <p>Два входа: {@link #validate(Ticket)} — общий список строк (CSV/сервис),
 * {@link #validateByField(Ticket)} — то же самое, но с привязкой к полю
 * (диалог правки показывает ошибку ровно под своим инпутом).
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

    /** Плоский список нарушений — используется CSV-загрузчиком и QueueService. */
    public static List<String> validate(Ticket ticket) {
        List<String> errors = new ArrayList<>();
        validateByField(ticket).values().forEach(errors::addAll);
        return List.copyOf(errors);
    }

    /** Нарушения с привязкой к полю; пустые поля в карту не попадают. */
    public static Map<TicketField, List<String>> validateByField(Ticket ticket) {
        Map<TicketField, List<String>> errors = new EnumMap<>(TicketField.class);

        String card = ticket.getCardNumber();
        if (card.isBlank()) {
            put(errors, TicketField.CARD_NUMBER, "Номер карты обязателен");
        } else if (!card.matches("\\d{4,20}")) {
            put(errors, TicketField.CARD_NUMBER, "Номер карты должен содержать от 4 до 20 цифр");
        }

        String name = ticket.getFullName();
        if (name.isBlank()) {
            put(errors, TicketField.FULL_NAME, "ФИО обязательно");
        } else if (containsLineBreak(name)) {
            put(errors, TicketField.FULL_NAME, "ФИО должно занимать одну строку");
        } else if (!FULL_NAME_PATTERN.matcher(name).matches()) {
            put(errors, TicketField.FULL_NAME,
                    "ФИО должно содержать 2–3 слова, каждое с заглавной буквы");
        }

        String office = ticket.getOffice();
        if (office.isBlank()) {
            put(errors, TicketField.OFFICE, "Кабинет обязателен");
        } else if (containsLineBreak(office)) {
            put(errors, TicketField.OFFICE, "Кабинет должен занимать одну строку");
        } else if (!OFFICE_PATTERN.matcher(office).matches()) {
            put(errors, TicketField.OFFICE,
                    "Кабинет должен быть числом с возможной буквой (например, 104 или 104а)");
        }

        int urgency = ticket.getUrgency();
        if (urgency < 0 || urgency > 3) {
            put(errors, TicketField.URGENCY, "Срочность должна быть от 0 до 3");
        }

        if (ticket.getTakenAt() == null) {
            put(errors, TicketField.TAKEN_AT, "Время взятия обязательно");
        }

        if (ticket instanceof HomeVisitTicket home) {
            String address = home.getAddress();
            if (address.isBlank()) {
                put(errors, TicketField.ADDRESS, "Для талона «На дому» нужен адрес");
            } else if (containsLineBreak(address)) {
                put(errors, TicketField.ADDRESS, "Адрес должен занимать одну строку");
            }
        }

        return errors;
    }

    public static boolean containsLineBreak(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }

    private static void put(Map<TicketField, List<String>> map, TicketField field, String message) {
        map.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
    }
}