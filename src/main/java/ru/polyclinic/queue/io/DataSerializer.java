package ru.polyclinic.queue.io;

import java.io.File;
import java.util.List;
import ru.polyclinic.queue.model.Ticket;

public interface DataSerializer {
    void save(File file, List<Ticket> tickets) throws Exception;
    List<Ticket> load(File file) throws Exception;
    String getFormatName();
}