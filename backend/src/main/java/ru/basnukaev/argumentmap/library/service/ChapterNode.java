package ru.basnukaev.argumentmap.library.service;

import java.util.List;

import ru.basnukaev.argumentmap.library.domain.Chapter;

public record ChapterNode(Chapter chapter, List<ChapterNode> children) {
}
