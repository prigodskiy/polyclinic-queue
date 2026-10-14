package ru.student.clinicqueue.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.student.clinicqueue.model.ClosedTicket;
import ru.student.clinicqueue.model.HomeVisitTicket;
import ru.student.clinicqueue.model.RegularTicket;
import ru.student.clinicqueue.model.Ticket;
import ru.student.clinicqueue.model.TicketType;
import ru.student.clinicqueue.model.TicketValidator;

public final class TicketCsvService {
    private static final char DELIMITER = ';';
    private static final List<String> HEADER = List.of(
            "type",
            "card_number",
            "patient_name",
            "room",
            "urgency",
            "taken_at",
            "address"
    );

    public CsvLoadResult load(Path path) throws TicketCsvException {
        var tickets = new ArrayList<Ticket>();
        var errors = new ArrayList<CsvRowError>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            validateHeader(reader.readLine());

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                try {
                    tickets.add(parseTicket(line));
                } catch (TicketCsvException error) {
                    errors.add(new CsvRowError(lineNumber, error.getCode(), error.getMessage()));
                }
            }
        } catch (IOException error) {
            throw new TicketCsvException(
                    CsvErrorCode.IO_ERROR,
                    "Не удалось прочитать файл: " + path,
                    error
            );
        }

        return new CsvLoadResult(tickets, errors);
    }

    public void save(Path path, List<Ticket> tickets) throws TicketCsvException {
        Path absolutePath = path.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent == null) {
            throw new TicketCsvException(CsvErrorCode.IO_ERROR, "Не удалось определить папку файла");
        }

        Path temporaryFile = null;
        try {
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, ".tickets-", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                writer.write(String.join(Character.toString(DELIMITER), HEADER));
                writer.newLine();

                for (Ticket ticket : tickets) {
                    validateBeforeSave(ticket);
                    writer.write(formatTicket(ticket));
                    writer.newLine();
                }
            }

            moveReplacing(temporaryFile, absolutePath);
            temporaryFile = null;
        } catch (IOException error) {
            throw new TicketCsvException(
                    CsvErrorCode.IO_ERROR,
                    "Не удалось сохранить файл: " + path,
                    error
            );
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    temporaryFile.toFile().deleteOnExit();
                }
            }
        }
    }

    private void validateHeader(String headerLine) throws TicketCsvException {
        if (headerLine == null) {
            throw new TicketCsvException(CsvErrorCode.MISSING_HEADER, "Файл пуст");
        }

        String normalized = headerLine.startsWith("\uFEFF") ? headerLine.substring(1) : headerLine;
        if (!parseLine(normalized).equals(HEADER)) {
            throw new TicketCsvException(
                    CsvErrorCode.MISSING_HEADER,
                    "Ожидается заголовок: " + String.join(Character.toString(DELIMITER), HEADER)
            );
        }
    }

    private Ticket parseTicket(String line) throws TicketCsvException {
        List<String> fields = parseLine(line);
        if (fields.size() != HEADER.size()) {
            throw new TicketCsvException(
                    CsvErrorCode.WRONG_FIELD_COUNT,
                    "Ожидалось полей: %d, получено: %d".formatted(HEADER.size(), fields.size())
            );
        }

        TicketType type;
        try {
            type = TicketType.valueOf(fields.get(0).strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new TicketCsvException(CsvErrorCode.UNKNOWN_TYPE, "Неизвестный тип: " + fields.get(0));
        }

        String cardNumber = fields.get(1);
        String patientName = fields.get(2);
        String room = fields.get(3);
        int urgency = parseUrgency(fields.get(4));
        LocalDateTime takenAt = parseTakenAt(fields.get(5));
        String address = fields.get(6);

        Ticket ticket = switch (type) {
            case REGULAR -> {
                requireEmptyAddress(address, type);
                yield new RegularTicket(cardNumber, patientName, room, urgency, takenAt);
            }
            case CLOSED -> {
                requireEmptyAddress(address, type);
                yield new ClosedTicket(cardNumber, patientName, room, urgency, takenAt);
            }
            case HOME_VISIT -> new HomeVisitTicket(
                    cardNumber,
                    patientName,
                    room,
                    urgency,
                    takenAt,
                    address
            );
        };

        List<String> validationErrors = TicketValidator.validate(ticket);
        if (!validationErrors.isEmpty()) {
            throw new TicketCsvException(
                    CsvErrorCode.INVALID_TICKET,
                    String.join("; ", validationErrors)
            );
        }
        return ticket;
    }

    private List<String> parseLine(String line) throws TicketCsvException {
        var fields = new ArrayList<String>();
        var current = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < line.length(); index++) {
            char symbol = line.charAt(index);
            if (symbol == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else if (!quoted && current.length() > 0) {
                    throw new TicketCsvException(
                            CsvErrorCode.MALFORMED_QUOTES,
                            "Кавычка должна находиться в начале поля"
                    );
                } else {
                    quoted = !quoted;
                }
            } else if (symbol == DELIMITER && !quoted) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(symbol);
            }
        }

        if (quoted) {
            throw new TicketCsvException(CsvErrorCode.MALFORMED_QUOTES, "Незакрытая кавычка");
        }
        fields.add(current.toString());
        return fields;
    }

    private int parseUrgency(String value) throws TicketCsvException {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException error) {
            throw new TicketCsvException(CsvErrorCode.BAD_NUMBER, "Срочность должна быть целым числом");
        }
    }

    private LocalDateTime parseTakenAt(String value) throws TicketCsvException {
        try {
            return LocalDateTime.parse(value.strip());
        } catch (DateTimeParseException error) {
            throw new TicketCsvException(
                    CsvErrorCode.BAD_DATE_TIME,
                    "Время должно быть в ISO-формате, например 2026-10-12T09:30:00"
            );
        }
    }

    private void requireEmptyAddress(String address, TicketType type) throws TicketCsvException {
        if (!address.isBlank()) {
            throw new TicketCsvException(
                    CsvErrorCode.UNEXPECTED_DATA,
                    "Поле address недоступно для типа " + type.name()
            );
        }
    }

    private void validateBeforeSave(Ticket ticket) throws TicketCsvException {
        List<String> errors = TicketValidator.validate(ticket);
        if (!errors.isEmpty()) {
            throw new TicketCsvException(
                    CsvErrorCode.INVALID_TICKET,
                    "Нельзя сохранить талон карты %s: %s".formatted(
                            ticket.getCardNumber(),
                            String.join("; ", errors)
                    )
            );
        }
    }

    private String formatTicket(Ticket ticket) {
        List<String> fields = List.of(
                ticket.getType().name(),
                ticket.getCardNumber(),
                ticket.getPatientName(),
                ticket.getRoom(),
                ticket.getUrgencyText(),
                ticket.getTakenAt().toString(),
                ticket instanceof HomeVisitTicket homeVisit ? homeVisit.getAddress() : ""
        );
        return fields.stream().map(this::escape).reduce((left, right) -> left + DELIMITER + right).orElse("");
    }

    private String escape(String value) {
        if (value.indexOf(DELIMITER) >= 0 || value.indexOf('"') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
