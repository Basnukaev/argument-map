package ru.basnukaev.argumentmap.exception;

import java.util.UUID;

/**
 * Бросается когда lib_book_members запись с указанным id не найдена либо
 * принадлежит другой книге (ADR-043 Amendment).
 */
public class BookMemberNotFoundException extends RuntimeException {

    private final UUID memberId;

    public BookMemberNotFoundException(UUID memberId) {
        super("Член книги не найден: " + memberId);
        this.memberId = memberId;
    }

    public UUID getMemberId() {
        return memberId;
    }
}
