package polyclinic.queue.io;

import polyclinic.queue.model.Ticket;
import polyclinic.queue.model.HomeVisitTicket;
import polyclinic.queue.model.ClosedTicket;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class TicketCsvLoader {

    private static final String DELIMITER = ";";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int EXPECTED_FIELDS = 8;

    private static final String TYPE_REGULAR = "Обычный";
    private static final String TYPE_HOME = "На дому";
    private static final String TYPE_CLOSED = "Закрытый";

    public static List<Ticket> loadFromFile(String filePath) throws IOException, CsvParseException {
        List<Ticket> tickets = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (lineNumber == 1) continue;
                if (line.trim().isEmpty()) continue;

                Ticket ticket = parseLine(line, lineNumber);
                tickets.add(ticket);
            }
        }

        return tickets;
    }

    public static void saveToFile(String filePath, List<Ticket> tickets) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write("cardNumber,fullName,office,urgency,takenAt,type,address,closedAt");
            writer.newLine();

            for (Ticket ticket : tickets) {
                String type;
                String address = "";
                String closedAt = "";

                if (ticket instanceof HomeVisitTicket homeTicket) {
                    type = TYPE_HOME;
                    address = homeTicket.getAddress();
                } else if (ticket instanceof ClosedTicket closedTicket) {
                    type = TYPE_CLOSED;
                    closedAt = closedTicket.getClosedAt().format(DATE_FORMATTER);
                } else {
                    type = TYPE_REGULAR;
                }

                String line = String.join(DELIMITER,
                        ticket.getCardNumber(),
                        ticket.getFullName(),
                        String.valueOf(ticket.getOffice()),
                        String.valueOf(ticket.getUrgency()),
                        ticket.getTakenAt().format(DATE_FORMATTER),
                        type,
                        address,
                        closedAt
                );
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private static Ticket parseLine(String line, int lineNumber) throws CsvParseException {
        String[] parts = line.split(DELIMITER, -1);  // -1 сохраняет пустые поля в конце

        if (parts.length != EXPECTED_FIELDS) {
            throw new WrongFieldCountException(lineNumber, EXPECTED_FIELDS, parts.length);
        }

        String cardNumber = parts[0].trim();
        String fullName = parts[1].trim();
        String officeStr = parts[2].trim();
        String urgencyStr = parts[3].trim();
        String takenAtStr = parts[4].trim();
        String type = parts[5].trim();
        String address = parts[6].trim();
        String closedAtStr = parts[7].trim();

        int office;
        try {
            office = Integer.parseInt(officeStr);
        } catch (NumberFormatException e) {
            throw new BadNumberException(lineNumber, "office", officeStr);
        }

        int urgency;
        try {
            urgency = Integer.parseInt(urgencyStr);
        } catch (NumberFormatException e) {
            throw new BadNumberException(lineNumber, "urgency", urgencyStr);
        }

        if (urgency < 0 || urgency > 3) {
            throw new InvalidUrgencyException(lineNumber, urgency);
        }

        LocalDateTime takenAt;
        try {
            takenAt = LocalDateTime.parse(takenAtStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CsvParseException(lineNumber, "Неверный формат даты/времени: " + takenAtStr) {
                @Override
                public String getErrorCode() {
                    return "BAD_DATE_FORMAT";
                }
            };
        }

        return switch (type) {
            case TYPE_HOME -> {
                if (address.isEmpty()) {
                    throw new CsvParseException(lineNumber, "Для талона 'На дому' должен быть указан адрес") {
                        @Override
                        public String getErrorCode() {
                            return "MISSING_ADDRESS";
                        }
                    };
                }
                yield HomeVisitTicket.createWithTime(cardNumber, fullName, office, urgency, address, takenAt);
            }
            case TYPE_CLOSED -> {
                if (closedAtStr.isEmpty()) {
                    throw new CsvParseException(lineNumber, "Для закрытого талона должно быть указано время закрытия") {
                        @Override
                        public String getErrorCode() {
                            return "MISSING_CLOSED_AT";
                        }
                    };
                }
                LocalDateTime closedAt;
                try {
                    closedAt = LocalDateTime.parse(closedAtStr, DATE_FORMATTER);
                } catch (DateTimeParseException e) {
                    throw new CsvParseException(lineNumber, "Неверный формат времени закрытия: " + closedAtStr) {
                        @Override
                        public String getErrorCode() {
                            return "BAD_DATE_FORMAT";
                        }
                    };
                }
                yield ClosedTicket.createWithTime(cardNumber, fullName, office, urgency, takenAt, closedAt);
            }
            case TYPE_REGULAR -> Ticket.createWithTime(cardNumber, fullName, office, urgency, takenAt);
            default -> throw new CsvParseException(lineNumber, "Неизвестный тип талона: " + type) {
                @Override
                public String getErrorCode() {
                    return "UNKNOWN_TYPE";
                }
            };
        };
    }
}