package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.Topic;

@Repository
public class TopicRepository {

    private static final String COLUMNS = "id, title, description, root_node_id, created_by, created_at";

    private static final RowMapper<Topic> ROW_MAPPER = (rs, rn) -> new Topic(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("description"),
            rs.getObject("root_node_id", UUID.class),
            rs.getObject("created_by", UUID.class),
            instant(rs, "created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public TopicRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Topic save(Topic topic) {
        jdbcTemplate.update(
                "INSERT INTO topics (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?)",
                topic.id(),
                topic.title(),
                topic.description(),
                topic.rootNodeId(),
                topic.createdBy(),
                odt(topic.createdAt())
        );
        return topic;
    }

    public Optional<Topic> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM topics WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    public List<Topic> findAll() {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM topics ORDER BY created_at", ROW_MAPPER);
    }

    public void updateRootNodeId(UUID topicId, UUID rootNodeId) {
        jdbcTemplate.update("UPDATE topics SET root_node_id = ? WHERE id = ?", rootNodeId, topicId);
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM topics WHERE id = ?", id) > 0;
    }
}
