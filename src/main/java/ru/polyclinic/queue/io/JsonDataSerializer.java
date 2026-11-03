package ru.polyclinic.queue.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.util.List;
import ru.polyclinic.queue.model.Ticket;

public class JsonDataSerializer implements DataSerializer {
    private final ObjectMapper mapper;

    public JsonDataSerializer() {
        mapper = new ObjectMapper();
        // Чтобы Jackson понимал LocalDateTime
        mapper.registerModule(new JavaTimeModule());
        // Чтобы красиво выводил (с отступами)
        mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void save(File file, List<Ticket> tickets) throws Exception {
        mapper.writeValue(file, tickets);
    }

    @Override
    public List<Ticket> load(File file) throws Exception {
        // Используем TypeReference, чтобы Jackson знал, что это список талонов
        return mapper.readValue(file, new com.fasterxml.jackson.core.type.TypeReference<List<Ticket>>() {});
    }

    @Override
    public String getFormatName() { return "JSON"; }

    @Override
    public String toString() {
        return getFormatName();
    }
}