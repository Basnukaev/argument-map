package ru.basnukaev.argumentmap.repository;

import static ru.basnukaev.argumentmap.repository.JdbcTimes.instant;
import static ru.basnukaev.argumentmap.repository.JdbcTimes.odt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import ru.basnukaev.argumentmap.domain.TopicMember;

/**
 * Доступ к topic_members (ADR-043). M:N линковка SHARED-темы и
 * со-редакторов. JDBC Template, snake_case columns.
 */
@Repository
public class TopicMemberRepository {

    private static final String COLUMNS =
            "id, topic_id, user_id, role, added_at, added_by";

    private static final RowMapper<TopicMember> ROW_MAPPER = (rs, rn) -> new TopicMember(
            rs.getObject("id", UUID.class),
            rs.getObject("topic_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("role"),
            instant(rs, "added_at"),
            rs.getObject("added_by", UUID.class)
    );

    private final JdbcTemplate jdbcTemplate;

    public TopicMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TopicMember save(TopicMember member) {
        jdbcTemplate.update(
                "INSERT INTO topic_members (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?)",
                member.id(),
                member.topicId(),
                member.userId(),
                member.role(),
                odt(member.addedAt()),
                member.addedBy()
        );
        return member;
    }

    public Optional<TopicMember> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM topic_members WHERE id = ?",
                ROW_MAPPER, id
        ).stream().findFirst();
    }

    public List<TopicMember> findByTopicId(UUID topicId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM topic_members WHERE topic_id = ? ORDER BY added_at",
                ROW_MAPPER, topicId
        );
    }

    public List<TopicMember> findByUserId(UUID userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM topic_members WHERE user_id = ? ORDER BY added_at",
                ROW_MAPPER, userId
        );
    }

    public Optional<TopicMember> findByTopicAndUser(UUID topicId, UUID userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM topic_members WHERE topic_id = ? AND user_id = ?",
                ROW_MAPPER, topicId, userId
        ).stream().findFirst();
    }

    public boolean existsByTopicAndUser(UUID topicId, UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM topic_members WHERE topic_id = ? AND user_id = ?",
                Integer.class, topicId, userId
        );
        return count != null && count > 0;
    }

    public boolean delete(UUID id) {
        return jdbcTemplate.update("DELETE FROM topic_members WHERE id = ?", id) > 0;
    }

    public void updateRole(UUID id, String newRole) {
        jdbcTemplate.update("UPDATE topic_members SET role = ? WHERE id = ?", newRole, id);
    }
}
