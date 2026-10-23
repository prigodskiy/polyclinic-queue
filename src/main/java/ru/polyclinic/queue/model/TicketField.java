package ru.polyclinic.queue.model;

/** Поля талона — ключи попунктной валидации для диалога (GUI подсказывает ошибку ровно под нужным полем). */
public enum TicketField {
    CARD_NUMBER,
    FULL_NAME,
    OFFICE,
    URGENCY,
    TAKEN_AT,
    ADDRESS
}