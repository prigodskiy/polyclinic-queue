package ru.polyclinic.queue.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import ru.polyclinic.queue.model.ClosedTicket;
import ru.polyclinic.queue.model.HomeVisitTicket;
import ru.polyclinic.queue.model.Ticket;
import ru.polyclinic.queue.model.TicketType;

/**
 * Сервис для загрузки/сохранения талонов в CSV.
 * Формат: id;type;card_number;patient_name;office;urgency;taken_at;address
 */
public class TicketCsvService {

    private static final String SEPARATOR = ";";
    private static final String HEADER = "id;type;card_number;patient_name;office;urgency;taken_at;address";

    /**
     * Загрузка CSV без прогресса (обратная совместимость).
     */
    public CsvLoadResult load(Path path) throws TicketCsvException {
        return load(path, null);
    }

    /**
     * Загрузка CSV с колбэком прогресса.
     *
     * @param path     путь к файлу
     * @param progress колбэк (прочитано строк, всего строк); может быть null
     * @return результат загрузки
     * @throws TicketCsvException если файл не найден или не читается
     */
    public CsvLoadResult load(Path path, BiConsumer<Integer, Integer> progress) throws TicketCsvException {
        if (!Files.exists(path)) {
            throw new TicketCsvException(CsvErrorCode.FILE_NOT_FOUND, "Файл не найден: " + path);
        }

        List<Ticket> tickets = new ArrayList<>();
        List<CsvRowError> errors = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            // Пропускаем заголовок (если есть)
            String headerLine = reader.readLine();
            // Если заголовка нет или он неправильный — просто продолжаем

            // Считаем строки для прогресса
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }

            int total = lines.size();
            int processed = 0;

            for (int i = 0; i < lines.size(); i++) {
                // Проверка отмены — проверяем и interrupt, и cancelled
                if (Thread.currentThread().isInterrupted()) {
                    throw new TicketCsvException(CsvErrorCode.CANCELLED, "Загрузка отменена пользователем");
                }

                String currentLine = lines.get(i);
                int lineNumber = i + 2; // +2: заголовок + 1-based

                try {
                    Ticket ticket = parseLine(currentLine, lineNumber);
                    tickets.add(ticket);
                } catch (Exception e) {
                    errors.add(new CsvRowError(lineNumber, CsvErrorCode.PARSE_ERROR, e.getMessage()));
                }

                processed++;
                // Обновляем прогресс чаще — каждые 50 строк
                if (progress != null && (processed % 50 == 0 || processed == total)) {
                    progress.accept(processed, total);
                }
            }

        } catch (IOException e) {
            throw new TicketCsvException(CsvErrorCode.IO_ERROR, "Ошибка чтения файла: " + e.getMessage());
        }

        return new CsvLoadResult(tickets, errors);
    }

    private Ticket parseLine(String line, int lineNumber) {
        String[] parts = line.split(SEPARATOR, -1);
        if (parts.length < 7) {
            throw new IllegalArgumentException("Строка %d: недостаточно полей (ожидается 7-8, получено %d)"
                    .formatted(lineNumber, parts.length));
        }

        try {
            long id = Long.parseLong(parts[0].strip());
            String typeStr = parts[1].strip();
            String cardNumber = parts[2].strip();
            String patientName = parts[3].strip();
            String office = parts[4].strip();
            int urgency = Integer.parseInt(parts[5].strip());
            LocalDateTime takenAt = LocalDateTime.parse(parts[6].strip());
            String address = parts.length > 7 ? parts[7].strip() : null;

            TicketType type = TicketType.valueOf(typeStr);

            return switch (type) {
                case REGULAR -> new Ticket(id, cardNumber, patientName, office, urgency, takenAt);
                case HOME_VISIT -> {
                    if (address == null || address.isBlank()) {
                        throw new IllegalArgumentException("Строка %d: для талона на дому нужен адрес".formatted(lineNumber));
                    }
                    yield new HomeVisitTicket(id, cardNumber, patientName, office, urgency, takenAt, address);
                }
                case CLOSED -> new ClosedTicket(id, cardNumber, patientName, office, urgency, takenAt, address);
            };

        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Строка %d: неверный формат даты: %s".formatted(lineNumber, parts[6]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Строка %d: неверный числовой формат: %s".formatted(lineNumber, e.getMessage()));
        }
    }

    /**
     * Сохранение талонов в CSV.
     */
    public void save(Path path, List<Ticket> tickets) throws TicketCsvException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(HEADER);
            writer.newLine();

            for (Ticket ticket : tickets) {
                writer.write(formatLine(ticket));
                writer.newLine();
            }

        } catch (IOException e) {
            throw new TicketCsvException(CsvErrorCode.IO_ERROR, "Ошибка записи файла: " + e.getMessage());
        }
    }

    private String formatLine(Ticket ticket) {
        String address = "";
        if (ticket instanceof HomeVisitTicket hvt) {
            address = hvt.getAddress();
        } else if (ticket instanceof ClosedTicket ct && ct.getAddressOrNull() != null) {
            address = ct.getAddressOrNull();
        }

        return "%d;%s;%s;%s;%s;%d;%s;%s".formatted(
                ticket.getId(),
                ticket.getType(),
                ticket.getCardNumber(),
                ticket.getFullName(),
                ticket.getOffice(),
                ticket.getUrgency(),
                ticket.getTakenAt(),
                address
        );
    }
}