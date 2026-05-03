package ru.basnukaev.argumentmap.web.dto;

import java.util.List;

public record GraphResponse(
        TopicResponse topic,
        List<NodeResponse> nodes,
        List<EdgeResponse> edges
) {
}
