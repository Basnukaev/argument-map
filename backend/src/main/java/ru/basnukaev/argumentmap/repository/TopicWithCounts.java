package ru.basnukaev.argumentmap.repository;

import ru.basnukaev.argumentmap.domain.Topic;

/**
 * Тема + агрегаты графа: число узлов и рёбер темы. Возвращается
 * методами TopicRepository.findAllWithCounts/findByIdWithCounts -
 * для отображения карточки темы в списке без N+1 запросов на
 * подсчёт. См. ADR-016.
 */
public record TopicWithCounts(Topic topic, int nodeCount, int edgeCount) {
}
