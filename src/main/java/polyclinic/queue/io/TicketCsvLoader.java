package polyclinic.queue.io;

import polyclinic.queue.model.Ticket;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class TicketCsvLoader {

    private static final String DELIMITER = ",";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int EXPECTED_FIELDS = 5;

    public static List<Ticket> loadFromFile(String filePath) throws IOException, CsvParseException {
        List<Ticket> tickets = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (lineNumber == 1) continue; // заголовок
                if (line.trim().isEmpty()) continue;

                Ticket ticket = parseLine(line, lineNumber);
                tickets.add(ticket);
            }
        }

        return tickets;
    }

    public static void saveToFile(String filePath, List<Ticket> tickets) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write("cardNumber,fullName,office,urgency,takenAt");
            writer.newLine();

            for (Ticket ticket : tickets) {
                String line = String.join(DELIMITER,
                        ticket.getCardNumber(),
                        ticket.getFullName(),
                        String.valueOf(ticket.getOffice()),
                        String.valueOf(ticket.getUrgency()),
                        ticket.getTakenAt().format(DATE_FORMATTER)
                );
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private static Ticket parseLine(String line, int lineNumber) throws CsvParseException {
        String[] parts = line.split(DELIMITER);

        if (parts.length != EXPECTED_FIELDS) {
            throw new WrongFieldCountException(lineNumber, EXPECTED_FIELDS, parts.length);
        }

        String cardNumber = parts[0].trim();
        String fullName = parts[1].trim();
        String officeStr = parts[2].trim();
        String urgencyStr = parts[3].trim();
        String takenAtStr = parts[4].trim();

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
            throw new CsvParseException(lineNumber,
                    "Неверный формат даты/времени: " + takenAtStr) {
                @Override
                public String getErrorCode() {
                    return "BAD_DATE_FORMAT";
                }
            };
        }

        return Ticket.createWithTime(cardNumber, fullName, office, urgency, takenAt);
    }
}