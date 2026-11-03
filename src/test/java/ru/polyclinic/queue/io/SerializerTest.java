package ru.polyclinic.queue.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.polyclinic.queue.model.ClosedTicket;
import ru.polyclinic.queue.model.HomeVisitTicket;
import ru.polyclinic.queue.model.Ticket;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SerializerTest {

    @TempDir
    Path tempDir;

    // Вспомогательный метод для создания тестовых данных
    private List<Ticket> createTestData() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 23, 10, 0);
        return List.of(
                new Ticket(1L, "100", "Иван", "101", 2, time),
                new HomeVisitTicket(2L, "200", "Петр", "102", 3, time, "ул. Ленина, д. 1"),
                new ClosedTicket(3L, "300", "Анна", "103", 0, time, null)
        );
    }

    // Универсальный метод для проверки любого сериализатора
    private void testSerializer(DataSerializer serializer, String filename) throws Exception {
        File file = tempDir.resolve(filename).toFile();
        List<Ticket> originalData = createTestData();

        // 1. Сохраняем
        serializer.save(file, originalData);
        assertTrue(file.exists(), "Файл должен быть создан");

        // 2. Загружаем
        List<Ticket> loadedData = serializer.load(file);

        // 3. Проверяем
        assertEquals(3, loadedData.size(), "Должно загрузиться 3 талона");

        // Проверка обычного талона
        assertInstanceOf(Ticket.class, loadedData.get(0));
        assertEquals("Иван", loadedData.get(0).getFullName());
        assertEquals("100", loadedData.get(0).getCardNumber());

        // Проверка полиморфизма (Домашний вызов)
        assertInstanceOf(HomeVisitTicket.class, loadedData.get(1));
        HomeVisitTicket hv = (HomeVisitTicket) loadedData.get(1);
        assertEquals("ул. Ленина, д. 1", hv.getAddress());

        // Проверка полиморфизма (Закрытый талон)
        assertInstanceOf(ClosedTicket.class, loadedData.get(2));
        ClosedTicket ct = (ClosedTicket) loadedData.get(2);
        assertNull(ct.getAddressOrNull());
    }

    @Test
    void testJsonSerialization() throws Exception {
        testSerializer(new JsonDataSerializer(), "test.json");
    }

    @Test
    void testBinarySerialization() throws Exception {
        testSerializer(new BinaryDataSerializer(), "test.bin");
    }

    @Test
    void testCsvSerialization() throws Exception {
        testSerializer(new CsvDataSerializer(), "test.csv");
    }
}