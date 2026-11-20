package ru.polyclinic.queue.io;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import ru.polyclinic.queue.model.Ticket;

public class CsvDataSerializer implements DataSerializer {
    private final TicketCsvService csv = new TicketCsvService();
    private CsvLoadResult lastResult;

    @Override
    public void save(File file, List<Ticket> tickets) throws Exception {
        csv.save(Path.of(file.toURI()), tickets);
    }

    public void saveAsV1(File file, List<Ticket> tickets) throws Exception {
        csv.saveV1(file.toPath(), tickets);
    }

    public void saveAsV2(File file, List<Ticket> tickets) throws Exception {
        csv.saveV2(file.toPath(), tickets);
    }

    @Override
    public SerializerResult load(File file) throws Exception {
        lastResult = csv.load(file.toPath());

        CsvLoadResult result = csv.load(file.toPath());
        if (result.tickets().isEmpty() && !result.errors().isEmpty()) {
            throw new Exception("Ошибка CSV: " + result.errors().get(0).message());
        }
        return new SerializerResult(result.tickets(), result.version());
    }

    public CsvLoadResult getLastResult() {
        return lastResult;
    }

    @Override
    public String getFormatName() { return "CSV"; }

    @Override
    public String toString() { return getFormatName(); }
}