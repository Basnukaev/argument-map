package ru.basnukaev.argumentmap.library.web.controller;

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
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookMember;
import ru.basnukaev.argumentmap.library.domain.BookMemberRole;
import ru.basnukaev.argumentmap.library.domain.BookType;
import ru.basnukaev.argumentmap.library.domain.BookVisibility;
import ru.basnukaev.argumentmap.library.repository.BookMemberRepository;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.library.web.dto.AddBookMemberRequest;
import ru.basnukaev.argumentmap.library.web.dto.UpdateBookMemberRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class BookMemberControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookMemberRepository bookMemberRepository;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID bookId;

    @BeforeEach
    void setUp() {
        ownerId = insertUser("owner");
        otherUserId = insertUser("other");

        bookId = UUID.randomUUID();
        Instant now = Instant.now();
        bookRepository.save(new Book(
                bookId, BookType.BOOK, "T", null, "ar",
                null, null, ownerId, now, now,
                null, null, null, null, null, null,
                BookVisibility.SHARED
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
        bookMemberRepository.save(new BookMember(
                memberId, bookId, userId, role, Instant.now(), ownerId
        ));
        return memberId;
    }

    @Test
    void POST_addMember_ownerCanAdd_returns201() throws Exception {
        var req = new AddBookMemberRequest(otherUserId, BookMemberRole.MEMBER);

        mockMvc.perform(post("/api/v1/library/books/{bid}/members", bookId)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(otherUserId.toString()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.bookId").value(bookId.toString()));
    }

    @Test
    void POST_addMember_nonOwner_returns403() throws Exception {
        UUID someOtherUser = insertUser("third");
        var req = new AddBookMemberRequest(someOtherUser, BookMemberRole.MEMBER);

        mockMvc.perform(post("/api/v1/library/books/{bid}/members", bookId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-book-write")));
    }

    @Test
    void GET_listMembers_ownerCanList() throws Exception {
        addMember(otherUserId, BookMemberRole.EDITOR);

        mockMvc.perform(get("/api/v1/library/books/{bid}/members", bookId)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(otherUserId.toString()));
    }

    @Test
    void GET_listMembers_nonMemberOfPrivateBook_returns403() throws Exception {
        bookRepository.updateVisibility(bookId, BookVisibility.PRIVATE);

        mockMvc.perform(get("/api/v1/library/books/{bid}/members", bookId)
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(containsString("forbidden-book-access")));
    }

    @Test
    void DELETE_removeMember_memberCanRemoveSelf_returns204() throws Exception {
        UUID memberId = addMember(otherUserId, BookMemberRole.MEMBER);

        mockMvc.perform(delete("/api/v1/library/books/{bid}/members/{mid}", bookId, memberId)
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isNoContent());

        assert bookMemberRepository.findById(memberId).isEmpty();
    }

    @Test
    void DELETE_removeMember_nonOwnerCannotRemoveOther_returns403() throws Exception {
        UUID someUser = insertUser("third");
        UUID memberId = addMember(someUser, BookMemberRole.MEMBER);

        mockMvc.perform(delete("/api/v1/library/books/{bid}/members/{mid}", bookId, memberId)
                        .header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void DELETE_removeMember_ownerCanRemoveAnyone() throws Exception {
        UUID memberId = addMember(otherUserId, BookMemberRole.MEMBER);

        mockMvc.perform(delete("/api/v1/library/books/{bid}/members/{mid}", bookId, memberId)
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void PATCH_role_ownerCanChange() throws Exception {
        UUID memberId = addMember(otherUserId, BookMemberRole.MEMBER);
        var req = new UpdateBookMemberRequest(BookMemberRole.EDITOR);

        mockMvc.perform(patch("/api/v1/library/books/{bid}/members/{mid}", bookId, memberId)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EDITOR"));
    }

    @Test
    void PATCH_role_nonOwner_returns403() throws Exception {
        UUID memberId = addMember(otherUserId, BookMemberRole.MEMBER);
        var req = new UpdateBookMemberRequest(BookMemberRole.EDITOR);

        mockMvc.perform(patch("/api/v1/library/books/{bid}/members/{mid}", bookId, memberId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void POST_addMember_invalidRole_returns400() throws Exception {
        String json = "{\"userId\":\"" + otherUserId + "\",\"role\":\"SUPER_ADMIN\"}";

        mockMvc.perform(post("/api/v1/library/books/{bid}/members", bookId)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
