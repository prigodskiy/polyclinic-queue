package ru.polyclinic.queue.io;

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

import ru.polyclinic.queue.model.ClosedTicket;
import ru.polyclinic.queue.model.HomeVisitTicket;
import ru.polyclinic.queue.model.Ticket;
import ru.polyclinic.queue.model.TicketType;
import ru.polyclinic.queue.model.TicketValidator;

/**
 * CSV-кодек талонов — ЕДИНСТВЕННОЕ место, где модель превращается в строки.
 *
 * <p>Формат: {@code id;type;card_number;patient_name;office;urgency;taken_at;address}.
 * Колонка {@code type} — имя {@link TicketType}, без магических строк;
 * адрес пуст для обычных и закрытых немобильных талонов, поэтому адрес
 * закрытого «домашнего» талона не теряется (четвёртый класс не нужен).
 *
 * <p>Политика ошибок (лаба 1): заголовок обязателен — битый заголовок это
 * исключение; битые СТРОКИ пропускаются и собираются в warnings
 * ({@link CsvLoadResult}), GUI показывает их списком.
 *
 * <p>Сохранение атомарное: запись во временный файл + rename — очередь не
 * потеряется, если процесс упадёт посреди записи.
 */
public final class TicketCsvService {

    private static final char DELIMITER = ';';
    private static final List<String> HEADER = List.of(
            "id", "type", "card_number", "patient_name", "office", "urgency", "taken_at", "address");

    public CsvLoadResult load(Path path) throws TicketCsvException {
        List<Ticket> tickets = new ArrayList<>();
        List<CsvRowError> errors = new ArrayList<>();

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
            throw new TicketCsvException(CsvErrorCode.IO_ERROR,
                    "Не удалось прочитать файл: " + path, error);
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
                    writer.write(formatTicket(ticket));
                    writer.newLine();
                }
            }
            moveReplacing(temporaryFile, absolutePath);
            temporaryFile = null;
        } catch (IOException error) {
            throw new TicketCsvException(CsvErrorCode.IO_ERROR,
                    "Не удалось сохранить файл: " + path, error);
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

    // ---------- разбор ----------

    private void validateHeader(String headerLine) throws TicketCsvException {
        if (headerLine == null) {
            throw new TicketCsvException(CsvErrorCode.MISSING_HEADER, "Файл пуст");
        }
        String normalized = headerLine.startsWith("\uFEFF") ? headerLine.substring(1) : headerLine;
        if (!parseLine(normalized).equals(HEADER)) {
            throw new TicketCsvException(CsvErrorCode.MISSING_HEADER,
                    "Ожидается заголовок: " + String.join(Character.toString(DELIMITER), HEADER));
        }
    }

    private Ticket parseTicket(String line) throws TicketCsvException {
        List<String> fields = parseLine(line);
        if (fields.size() != HEADER.size()) {
            throw new TicketCsvException(CsvErrorCode.WRONG_FIELD_COUNT,
                    "Ожидалось полей: %d, получено: %d".formatted(HEADER.size(), fields.size()));
        }

        long id = parseId(fields.get(0));

        TicketType type;
        try {
            type = TicketType.valueOf(fields.get(1).strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new TicketCsvException(CsvErrorCode.UNKNOWN_TYPE, "Неизвестный тип: " + fields.get(1));
        }

        String cardNumber = fields.get(2);
        String patientName = fields.get(3);
        String office = fields.get(4);
        int urgency = parseUrgency(fields.get(5));
        // Вилка 0–3 проверяется конструктором Ticket как IllegalArgumentException —
        // перехватываем её здесь, иначе одна битая строка уронит всю загрузку.
        if (urgency < 0 || urgency > 3) {
            throw new TicketCsvException(CsvErrorCode.INVALID_TICKET,
                    "Срочность должна быть от 0 до 3, получено: " + urgency);
        }
        LocalDateTime takenAt = parseTakenAt(fields.get(6));
        String address = fields.get(7);

        Ticket ticket;
        try {
            ticket = switch (type) {
                case REGULAR -> new Ticket(id, cardNumber, patientName, office, urgency, takenAt);
                case HOME_VISIT -> {
                    if (address.isBlank()) {
                        throw new TicketCsvException(CsvErrorCode.MISSING_ADDRESS,
                                "Для талона «На дому» должен быть указан адрес");
                    }
                    yield new HomeVisitTicket(id, cardNumber, patientName, office, urgency, takenAt, address);
                }
                case CLOSED -> new ClosedTicket(id, cardNumber, patientName, office, urgency, takenAt, address);
            };
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new TicketCsvException(CsvErrorCode.INVALID_TICKET,
                    error.getMessage() == null ? "Битые поля строки" : error.getMessage());
        }

        List<String> validationErrors = ticket.validate();
        if (!validationErrors.isEmpty()) {
            throw new TicketCsvException(CsvErrorCode.INVALID_TICKET, String.join("; ", validationErrors));
        }
        return ticket;
    }

    /** Разбор строки с поддержкой кавычек ("...", "" внутри) — свой алгоритм, без split. */
    private List<String> parseLine(String line) throws TicketCsvException {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < line.length(); index++) {
            char symbol = line.charAt(index);
            if (symbol == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else if (!quoted && !current.isEmpty()) {
                    throw new TicketCsvException(CsvErrorCode.MALFORMED_QUOTES,
                            "Кавычка должна находиться в начале поля");
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

    private long parseId(String value) throws TicketCsvException {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException error) {
            throw new TicketCsvException(CsvErrorCode.BAD_NUMBER, "Id должен быть целым числом");
        }
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
            throw new TicketCsvException(CsvErrorCode.BAD_DATE_TIME,
                    "Время должно быть в ISO-формате, например 2026-10-12T09:30:00");
        }
    }

    // ---------- запись ----------

    private String formatTicket(Ticket ticket) {
        String address = "";
        if (ticket instanceof HomeVisitTicket home) {
            address = home.getAddress();
        } else if (ticket instanceof ClosedTicket closed && closed.getAddressOrNull() != null) {
            address = closed.getAddressOrNull();
        }
        List<String> fields = List.of(
                Long.toString(ticket.getId()),
                ticket.getType().name(),
                ticket.getCardNumber(),
                ticket.getFullName(),
                ticket.getOffice(),
                Integer.toString(ticket.getUrgency()),
                ticket.getTakenAt().toString(),
                address);
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                line.append(DELIMITER);
            }
            line.append(escape(fields.get(i)));
        }
        return line.toString();
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