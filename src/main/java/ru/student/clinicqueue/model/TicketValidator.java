package ru.student.clinicqueue.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class TicketValidator {
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("\\d{4,20}");

    private TicketValidator() {
    }

    public static List<String> validate(Ticket ticket) {
        var errors = new ArrayList<String>();

        if (!CARD_NUMBER_PATTERN.matcher(ticket.getCardNumber()).matches()) {
            errors.add("Номер карты должен содержать от 4 до 20 цифр");
        }

        if (ticket.getPatientName().isBlank()) {
            errors.add("ФИО обязательно");
        } else if (containsLineBreak(ticket.getPatientName())) {
            errors.add("ФИО должно занимать одну строку");
        }

        if (ticket.getRoom().isBlank()) {
            errors.add("Кабинет обязателен");
        } else if (containsLineBreak(ticket.getRoom())) {
            errors.add("Кабинет должен занимать одну строку");
        }

        if (ticket.getUrgency() < 0 || ticket.getUrgency() > 3) {
            errors.add("Срочность должна быть от 0 до 3");
        }

        if (ticket.getTakenAt() == null) {
            errors.add("Время взятия обязательно");
        }

        if (ticket instanceof HomeVisitTicket homeVisit) {
            if (homeVisit.getAddress().isBlank()) {
                errors.add("Для талона на дому нужен адрес");
            } else if (containsLineBreak(homeVisit.getAddress())) {
                errors.add("Адрес должен занимать одну строку");
            }
        }

        return List.copyOf(errors);
    }

    private static boolean containsLineBreak(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }
}
