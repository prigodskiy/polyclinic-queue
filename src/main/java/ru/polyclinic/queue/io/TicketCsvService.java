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
import ru.polyclinic.queue.model.TicketValidator;

public class TicketCsvService {
    private static final String SEPARATOR = ";";

    private static final String HEADER_V1 = "id;type;card_number;patient_name;office;urgency;taken_at;address";
    private static final String HEADER_V2 = "id;type;card_number;patient_name;office;urgency;phone_number;taken_at;address";
    private static final String HEADER_V3 = "id;type;card_number;patient_name;office;urgency;phone_number;policy_number;taken_at;address";

    public CsvLoadResult load(Path path) throws TicketCsvException {
        return load(path, null);
    }

    public CsvLoadResult load(Path path, BiConsumer<Integer, Integer> progress) throws TicketCsvException {
        if (!Files.exists(path)) {
            throw new TicketCsvException(CsvErrorCode.FILE_NOT_FOUND, "файл не найден: " + path);
        }

        List<Ticket> tickets = new ArrayList<>();
        List<CsvRowError> errors = new ArrayList<>();
        int fileVersion = 3;

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String headerLine = reader.readLine();

            if (headerLine == null || !headerLine.contains("phone_number")) {
                fileVersion = 1;
            } else if (!headerLine.contains("policy_number")) {
                fileVersion = 2;
            }

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
                if (Thread.currentThread().isInterrupted()) {
                    throw new TicketCsvException(CsvErrorCode.CANCELLED, "загрузка отменена пользователем");
                }

                String currentLine = lines.get(i);
                int lineNumber = i + 2;

                try {
                    Ticket ticket = parseLine(currentLine, lineNumber, fileVersion);
                    tickets.add(ticket);
                } catch (Exception e) {
                    errors.add(new CsvRowError(lineNumber, CsvErrorCode.PARSE_ERROR, e.getMessage()));
                }

                processed++;
                if (progress != null && (processed % 50 == 0 || processed == total)) {
                    progress.accept(processed, total);
                }
            }

        } catch (IOException e) {
            throw new TicketCsvException(CsvErrorCode.IO_ERROR, "ошибка чтения файла: " + e.getMessage());
        }

        return new CsvLoadResult(tickets, errors, fileVersion);
    }

    private Ticket parseLine(String line, int lineNumber, int version) {
        String[] parts = splitCsvLine(line).toArray(new String[0]);

        int minRequiredCols = version == 1 ? 7 : (version == 2 ? 8 : 9);
        if (parts.length < minRequiredCols) {
            throw new IllegalArgumentException("Строка %d: недостаточно полей для версии %d".formatted(lineNumber, version));
        }

        try {
            long id = Long.parseLong(parts[0].strip());
            String typeStr = parts[1].strip();
            String cardNumber = parts[2].strip();
            String patientName = parts[3].strip();
            String office = parts[4].strip();
            int urgency = Integer.parseInt(parts[5].strip());

            String phoneNumber = version >= 2 ? parts[6].strip() : "Неизвестен";
            String policyNumber = version >= 3 ? parts[7].strip() : "Нет полиса";
            int takenAtIndex = version >= 3 ? 8 : (version == 2 ? 7 : 6);
            int addressIndex = version >= 3 ? 9 : (version == 2 ? 8 : 7);

            LocalDateTime takenAt = LocalDateTime.parse(parts[takenAtIndex].strip());
            String address = parts.length > addressIndex ? parts[addressIndex].strip() : null;

            TicketType type = TicketType.fromString(typeStr);

            Ticket ticket = switch (type) {
                case REGULAR -> new Ticket(id, cardNumber, patientName, office, urgency, phoneNumber, policyNumber, takenAt);
                case HOME_VISIT -> {
                    if (address == null || address.isBlank()) {
                        throw new IllegalArgumentException("Строка %d: для талона на дому нужен адрес".formatted(lineNumber));
                    }
                    yield new HomeVisitTicket(id, cardNumber, patientName, office, urgency, phoneNumber, policyNumber, takenAt, address);
                }
                case CLOSED -> new ClosedTicket(id, cardNumber, patientName, office, urgency, phoneNumber, policyNumber, takenAt, address);
            };

            List<String> validationErrors = TicketValidator.validate(ticket);
            if (!validationErrors.isEmpty()) {
                throw new IllegalArgumentException("Строка %d: %s".formatted(lineNumber, String.join("; ", validationErrors)));
            }
            return ticket;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Строка %d: неверный формат даты".formatted(lineNumber));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Строка %d: неверный числовой формат: %s".formatted(lineNumber, e.getMessage()));
        }
        // Наши IllegalArgumentException пролетают мимо этого catch, так что двойного "Строка X: Строка X" не будет
    }

    private String formatLine(Ticket ticket, int version) {
        String address = "";
        if (ticket instanceof HomeVisitTicket hvt) address = hvt.getAddress();
        else if (ticket instanceof ClosedTicket ct && ct.getAddressOrNull() != null) address = ct.getAddressOrNull();

        if (version >= 3) {
            return String.join(SEPARATOR,
                    escapeField(String.valueOf(ticket.getId())), escapeField(ticket.getType().toString()),
                    escapeField(ticket.getCardNumber()), escapeField(ticket.getFullName()),
                    escapeField(ticket.getOffice()), escapeField(String.valueOf(ticket.getUrgency())),
                    escapeField(ticket.getPhoneNumber()), escapeField(ticket.getPolicyNumber()),
                    escapeField(ticket.getTakenAt().toString()), escapeField(address));
        } else if (version == 2) {
            return String.join(SEPARATOR,
                    escapeField(String.valueOf(ticket.getId())), escapeField(ticket.getType().toString()),
                    escapeField(ticket.getCardNumber()), escapeField(ticket.getFullName()),
                    escapeField(ticket.getOffice()), escapeField(String.valueOf(ticket.getUrgency())),
                    escapeField(ticket.getPhoneNumber()), escapeField(ticket.getTakenAt().toString()),
                    escapeField(address));
        } else {
            return String.join(SEPARATOR,
                    escapeField(String.valueOf(ticket.getId())), escapeField(ticket.getType().toString()),
                    escapeField(ticket.getCardNumber()), escapeField(ticket.getFullName()),
                    escapeField(ticket.getOffice()), escapeField(String.valueOf(ticket.getUrgency())),
                    escapeField(ticket.getTakenAt().toString()), escapeField(address));
        }
    }

    public void save(Path path, List<Ticket> tickets) throws TicketCsvException {
        saveInternal(path, HEADER_V3, tickets, 3);
    }

    public void saveV1(Path path, List<Ticket> tickets) throws TicketCsvException {
        saveInternal(path, HEADER_V1, tickets, 1);
    }

    public void saveV2(Path path, List<Ticket> tickets) throws TicketCsvException {
        saveInternal(path, HEADER_V2, tickets, 2);
    }

    private void saveInternal(Path path, String header, List<Ticket> tickets, int version) throws TicketCsvException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(header);
            writer.newLine();
            for (Ticket ticket : tickets) {
                writer.write(formatLine(ticket, version));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new TicketCsvException(CsvErrorCode.IO_ERROR, "ошибка записи файла: " + e.getMessage());
        }
    }

    private static String escapeField(String value) {
        if (value == null) return "";
        if (value.indexOf(';') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"'); i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ';') {
                result.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        result.add(cur.toString());
        return result;
    }
}