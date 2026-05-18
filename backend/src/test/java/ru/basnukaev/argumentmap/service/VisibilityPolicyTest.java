package ru.basnukaev.argumentmap.service;

import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisibilityPolicyTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    private final Predicate<UUID> memberPredicate = uuid -> uuid.equals(MEMBER);
    private final Predicate<UUID> editorPredicate = uuid -> uuid.equals(MEMBER);

    // ---- canRead ----

    @Test
    void canRead_owner_alwaysAllowed() {
        for (String v : new String[]{"PRIVATE", "SHARED", "PUBLIC"}) {
            assertThat(VisibilityPolicy.canRead(v, OWNER, OWNER, memberPredicate))
                    .as("owner read on %s", v).isTrue();
        }
    }

    @Test
    void canRead_public_anyoneAllowed() {
        assertThat(VisibilityPolicy.canRead("PUBLIC", OWNER, STRANGER, memberPredicate)).isTrue();
        assertThat(VisibilityPolicy.canRead("PUBLIC", OWNER, MEMBER, memberPredicate)).isTrue();
    }

    @Test
    void canRead_shared_onlyMembers() {
        assertThat(VisibilityPolicy.canRead("SHARED", OWNER, MEMBER, memberPredicate)).isTrue();
        assertThat(VisibilityPolicy.canRead("SHARED", OWNER, STRANGER, memberPredicate)).isFalse();
    }

    @Test
    void canRead_private_onlyOwner() {
        assertThat(VisibilityPolicy.canRead("PRIVATE", OWNER, MEMBER, memberPredicate)).isFalse();
        assertThat(VisibilityPolicy.canRead("PRIVATE", OWNER, STRANGER, memberPredicate)).isFalse();
    }

    // ---- canWrite ----

    @Test
    void canWrite_owner_alwaysAllowed() {
        for (String v : new String[]{"PRIVATE", "SHARED", "PUBLIC"}) {
            assertThat(VisibilityPolicy.canWrite(v, OWNER, OWNER, editorPredicate))
                    .as("owner write on %s", v).isTrue();
        }
    }

    @Test
    void canWrite_private_onlyOwner() {
        assertThat(VisibilityPolicy.canWrite("PRIVATE", OWNER, MEMBER, editorPredicate)).isFalse();
        assertThat(VisibilityPolicy.canWrite("PRIVATE", OWNER, STRANGER, editorPredicate)).isFalse();
    }

    @Test
    void canWrite_sharedOrPublic_editorAllowed() {
        assertThat(VisibilityPolicy.canWrite("SHARED", OWNER, MEMBER, editorPredicate)).isTrue();
        assertThat(VisibilityPolicy.canWrite("PUBLIC", OWNER, MEMBER, editorPredicate)).isTrue();
    }

    @Test
    void canWrite_sharedOrPublic_strangerDenied() {
        assertThat(VisibilityPolicy.canWrite("SHARED", OWNER, STRANGER, editorPredicate)).isFalse();
        assertThat(VisibilityPolicy.canWrite("PUBLIC", OWNER, STRANGER, editorPredicate)).isFalse();
    }

    @Test
    void canRead_nullOwner_noOwnerMatch() {
        // Если ownerId null (например, удалённый user) - PRIVATE недоступен
        // всем, PUBLIC доступен всем, SHARED только members
        assertThat(VisibilityPolicy.canRead("PRIVATE", null, STRANGER, memberPredicate)).isFalse();
        assertThat(VisibilityPolicy.canRead("PUBLIC", null, STRANGER, memberPredicate)).isTrue();
        assertThat(VisibilityPolicy.canRead("SHARED", null, MEMBER, memberPredicate)).isTrue();
    }
}
