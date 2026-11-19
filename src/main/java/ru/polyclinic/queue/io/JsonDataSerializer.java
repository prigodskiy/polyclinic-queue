package ru.polyclinic.queue.io;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ru.polyclinic.queue.model.Ticket;
import ru.polyclinic.queue.model.TicketValidator;

public class JsonDataSerializer implements DataSerializer {
    private static final int CURRENT_VERSION = 3;
    private final ObjectMapper mapper;

    public JsonDataSerializer() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void save(File file, List<Ticket> tickets) throws Exception {
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("schema_version", CURRENT_VERSION);
        wrapper.put("tickets", tickets);
        mapper.writeValue(file, wrapper);
    }

    @Override
    public SerializerResult load(File file) throws Exception {
        JsonNode root;
        try {
            root = mapper.readTree(file);
        } catch (Exception e) {
            throw new IOException("Повреждённый JSON-файл: " + e.getMessage(), e);
        }

        if (root == null || root.isNull()) {
            throw new IOException("JSON-файл пуст");
        }

        int version;
        List<Ticket> tickets;

        try {
            if (root.isArray()) {
                version = 1;
                tickets = mapper.convertValue(root, new TypeReference<List<Ticket>>() {});
            } else if (root.isObject()) {
                JsonNode versionNode = root.get("schema_version");
                if (versionNode == null || versionNode.isNull()) {
                    version = CURRENT_VERSION;
                } else if (!versionNode.isInt()) {
                    throw new IOException("Некорректный формат поля schema_version");
                } else {
                    version = versionNode.asInt();
                }

                if (version < 1) {
                    throw new IOException("Неподдерживаемая версия схемы JSON: " + version);
                }
                if (version > CURRENT_VERSION) {
                    throw new IOException("Файл создан в более новой версии программы (schema_version=" + version + ")");
                }

                JsonNode ticketsNode = root.get("tickets");
                if (ticketsNode == null || !ticketsNode.isArray()) {
                    throw new IOException("В JSON-файле отсутствует массив 'tickets'");
                }

                tickets = mapper.convertValue(ticketsNode, new TypeReference<List<Ticket>>() {});
            } else {
                throw new IOException("Неверная корневая структура JSON (ожидался объект или массив)");
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Ошибка структуры данных при чтении JSON: " + e.getMessage(), e);
        }

        if (version == 1) {
            tickets = migrateV1toV2(tickets);
            tickets = migrateV2toV3(tickets);
        } else if (version == 2) {
            tickets = migrateV2toV3(tickets);
        }

        validateTickets(tickets);
        return new SerializerResult(tickets, version);
    }

    private void validateTickets(List<Ticket> tickets) throws IOException {
        for (int i = 0; i < tickets.size(); i++) {
            Ticket ticket = tickets.get(i);
            if (ticket == null) {
                throw new IOException("Запись #" + (i + 1) + " в JSON равна null");
            }
            List<String> errors = TicketValidator.validate(ticket);
            if (!errors.isEmpty()) {
                throw new IOException("Ошибка валидации записи #" + (i + 1) + ": " + String.join("; ", errors));
            }
        }
    }

    private List<Ticket> migrateV1toV2(List<Ticket> oldTickets) {
        List<Ticket> migrated = new ArrayList<>(oldTickets.size());
        for (Ticket t : oldTickets) {
            migrated.add(t.withChanges(null, null, null, "Неизвестен", null));
        }
        return migrated;
    }

    private List<Ticket> migrateV2toV3(List<Ticket> v2Tickets) {
        List<Ticket> migrated = new ArrayList<>(v2Tickets.size());
        for (Ticket t : v2Tickets) {
            migrated.add(t.withChanges(null, null, null, null, "Нет полиса"));
        }
        return migrated;
    }

    public void saveAsV1(File file, List<Ticket> tickets) throws Exception {
        List<Map<String, Object>> v1List = new ArrayList<>(tickets.size());
        for (Ticket t : tickets) {
            Map<String, Object> map = mapper.convertValue(t, new TypeReference<Map<String, Object>>() {});
            map.remove("phoneNumber");
            map.remove("policyNumber");
            v1List.add(map);
        }
        mapper.writeValue(file, v1List);
    }

    public void saveAsV2(File file, List<Ticket> tickets) throws Exception {
        List<Map<String, Object>> v2List = new ArrayList<>(tickets.size());
        for (Ticket t : tickets) {
            Map<String, Object> map = mapper.convertValue(t, new TypeReference<Map<String, Object>>() {});
            map.remove("policyNumber");
            v2List.add(map);
        }
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("schema_version", 2);
        wrapper.put("tickets", v2List);
        mapper.writeValue(file, wrapper);
    }

    @Override
    public String getFormatName() { return "JSON"; }
    @Override
    public String toString() { return getFormatName(); }
}
