package ru.basnukaev.argumentmap.web.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.service.GraphService;
import ru.basnukaev.argumentmap.service.TopicService;
import ru.basnukaev.argumentmap.web.CurrentUser;
import ru.basnukaev.argumentmap.web.dto.CreateTopicRequest;
import ru.basnukaev.argumentmap.web.dto.GraphResponse;
import ru.basnukaev.argumentmap.web.dto.TopicResponse;
import ru.basnukaev.argumentmap.web.mapper.DtoMappers;

@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;
    private final GraphService graphService;

    public TopicController(TopicService topicService, GraphService graphService) {
        this.topicService = topicService;
        this.graphService = graphService;
    }

    @PostMapping
    public ResponseEntity<TopicResponse> create(@Valid @RequestBody CreateTopicRequest request,
                                                @CurrentUser UUID userId) {
        Topic created = topicService.createTopic(
                request.title(), request.description(),
                request.rootQuestion(), userId
        );
        TopicResponse body = DtoMappers.toResponse(created);
        return ResponseEntity.created(URI.create("/api/v1/topics/" + created.id())).body(body);
    }

    @GetMapping
    public List<TopicResponse> list() {
        return topicService.listTopics().stream().map(DtoMappers::toResponse).toList();
    }

    @GetMapping("/{topicId}")
    public TopicResponse getOne(@PathVariable UUID topicId) {
        return DtoMappers.toResponse(topicService.getTopic(topicId));
    }

    @DeleteMapping("/{topicId}")
    public ResponseEntity<Void> delete(@PathVariable UUID topicId) {
        topicService.deleteTopic(topicId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{topicId}/graph")
    public GraphResponse getGraph(@PathVariable UUID topicId) {
        return DtoMappers.toResponse(graphService.getGraph(topicId));
    }
}
