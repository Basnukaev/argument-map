package ru.basnukaev.argumentmap.library.service;

import java.util.List;

import ru.basnukaev.argumentmap.library.domain.Book;

public record BookDetail(Book book, List<ChapterNode> rootChapters) {
}
