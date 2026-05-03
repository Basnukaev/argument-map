package ru.basnukaev.argumentmap.service;

import java.util.List;

import ru.basnukaev.argumentmap.domain.Edge;
import ru.basnukaev.argumentmap.domain.Node;
import ru.basnukaev.argumentmap.domain.Topic;

public record GraphView(Topic topic, List<Node> nodes, List<Edge> edges) {
}
