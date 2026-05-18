package ru.basnukaev.argumentmap.auth.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActorTest {

    @Test
    void user_factory_setsUserRole() {
        UUID id = UUID.randomUUID();
        Actor actor = Actor.user(id);
        assertThat(actor.userId()).isEqualTo(id);
        assertThat(actor.role()).isEqualTo(UserRole.USER);
        assertThat(actor.isUser()).isTrue();
        assertThat(actor.isAdmin()).isFalse();
    }

    @Test
    void admin_factory_setsAdminRole() {
        UUID id = UUID.randomUUID();
        Actor actor = Actor.admin(id);
        assertThat(actor.role()).isEqualTo(UserRole.ADMIN);
        assertThat(actor.isAdmin()).isTrue();
        assertThat(actor.isUser()).isFalse();
    }

    @Test
    void from_principal_copiesIdAndRole() {
        UUID id = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(id, "ali", "ali@example.com", UserRole.ADMIN);
        Actor actor = Actor.from(principal);
        assertThat(actor.userId()).isEqualTo(id);
        assertThat(actor.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void from_principal_nullRole_defaultsToUser() {
        UUID id = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(id, "ali", "ali@example.com", null);
        Actor actor = Actor.from(principal);
        assertThat(actor.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void constructor_rejectsNullUserId() {
        assertThatThrownBy(() -> new Actor(null, UserRole.USER))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void constructor_rejectsNullRole() {
        assertThatThrownBy(() -> new Actor(UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("role");
    }

    @Test
    void constructor_rejectsInvalidRole() {
        assertThatThrownBy(() -> new Actor(UUID.randomUUID(), "SUPERADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUPERADMIN");
    }
}
