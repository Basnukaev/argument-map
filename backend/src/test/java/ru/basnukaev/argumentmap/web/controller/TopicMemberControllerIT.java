package ru.basnukaev.argumentmap.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.TestcontainersConfiguration;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.domain.TopicMember;
import ru.basnukaev.argumentmap.domain.TopicMemberRole;
import ru.basnukaev.argumentmap.domain.TopicVisibility;
import ru.basnukaev.argumentmap.repository.TopicMemberRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;
import ru.basnukaev.argumentmap.web.dto.AddTopicMemberRequest;
import ru.basnukaev.argumentmap.web.dto.UpdateTopicMemberRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TopicMemberControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private TopicMemberRepository topicMemberRepository;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID topicId;

    @BeforeEach
    void setUp() {
        ownerId = insertUser("owner");
        otherUserId = insertUser("other");

        topicId = UUID.randomUUID();
        topicRepository.save(new Topic(
                topicId, "T", null, null, ownerId, Instant.now(),
                TopicVisibility.SHARED
        ));
    }

    private UUID insertUser(String suffix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email) VALUES (?, ?, ?)",
                id, "user-" + id + "-" + suffix, id + "-" + suffix + "@test.com"
        );
        return id;
    }

    private UUID addMember(UUID userId, String role) {
        UUID memberId = UUID.randomUUID();
        topicMemberRepository.save(new TopicMember(
                memberId, topicId, userId, role, Instant.now(), ownerId
        ));
        return memberId;
    }

    @Test
    void POST_addMember_ownerCanAdd_returns201() throws Exception {
        var req = new AddTopicMemberRequest(otherUserId, TopicMemberRole.MEMBER);

        mockMvc.perform(post("/api/v1/topics/{tid}/members", topicId)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(otherUserId.toString()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.topicId").value(topicId.toString()));
    }

    @Test
    void POST_addMember_nonOwner_returns403() throws Exception {
        UUID someOtherUser = insertUser("third");
        var req = new AddTopicMemberRequest(someOtherUser, TopicMemberRole.MEMBER);

        mockMvc.perform(post("/api/v1/topics/{tid}/members", topicId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-topic-write")));
    }

    @Test
    void GET_listMembers_ownerCanList() throws Exception {
        addMember(otherUserId, TopicMemberRole.EDITOR);

        mockMvc.perform(get("/api/v1/topics/{tid}/members", topicId)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(otherUserId.toString()));
    }

    @Test
    void GET_listMembers_nonMemberOfPrivateTopic_returns403() throws Exception {
        // переключим тему в PRIVATE - otherUser не member и не owner → 403
        topicRepository.updateVisibility(topicId, TopicVisibility.PRIVATE);

        mockMvc.perform(get("/api/v1/topics/{tid}/members", topicId)
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-topic-access")));
    }

    @Test
    void DELETE_removeMember_memberCanRemoveSelf_returns204() throws Exception {
        UUID memberId = addMember(otherUserId, TopicMemberRole.MEMBER);

        mockMvc.perform(delete("/api/v1/topics/{tid}/members/{mid}", topicId, memberId)
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isNoContent());

        assert topicMemberRepository.findById(memberId).isEmpty();
    }

    @Test
    void DELETE_removeMember_nonOwnerCannotRemoveOther_returns403() throws Exception {
        UUID someUser = insertUser("third");
        UUID memberId = addMember(someUser, TopicMemberRole.MEMBER);

        mockMvc.perform(delete("/api/v1/topics/{tid}/members/{mid}", topicId, memberId)
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void DELETE_removeMember_ownerCanRemoveAnyone() throws Exception {
        UUID memberId = addMember(otherUserId, TopicMemberRole.MEMBER);

        mockMvc.perform(delete("/api/v1/topics/{tid}/members/{mid}", topicId, memberId)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void PATCH_role_ownerCanChange() throws Exception {
        UUID memberId = addMember(otherUserId, TopicMemberRole.MEMBER);
        var req = new UpdateTopicMemberRequest(TopicMemberRole.EDITOR);

        mockMvc.perform(patch("/api/v1/topics/{tid}/members/{mid}", topicId, memberId)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EDITOR"));
    }

    @Test
    void PATCH_role_nonOwner_returns403() throws Exception {
        UUID memberId = addMember(otherUserId, TopicMemberRole.MEMBER);
        var req = new UpdateTopicMemberRequest(TopicMemberRole.EDITOR);

        mockMvc.perform(patch("/api/v1/topics/{tid}/members/{mid}", topicId, memberId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void POST_addMember_invalidRole_returns400() throws Exception {
        String json = "{\"userId\":\"" + otherUserId + "\",\"role\":\"SUPER_ADMIN\"}";

        mockMvc.perform(post("/api/v1/topics/{tid}/members", topicId)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
