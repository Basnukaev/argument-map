package ru.basnukaev.argumentmap.auth.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserRoleTest {

    @Test
    void isValid_returnsTrue_forAllFourRoles() {
        assertTrue(UserRole.isValid(UserRole.USER));
        assertTrue(UserRole.isValid(UserRole.STUDENT));
        assertTrue(UserRole.isValid(UserRole.SCHOLAR));
        assertTrue(UserRole.isValid(UserRole.ADMIN));
    }

    @Test
    void isValid_returnsFalse_forUnknown() {
        assertFalse(UserRole.isValid("MODERATOR"));
        assertFalse(UserRole.isValid("user")); // case-sensitive
        assertFalse(UserRole.isValid(""));
        assertFalse(UserRole.isValid(null));
    }

    @Test
    void hasAtLeast_admin_isHighestRole() {
        assertTrue(UserRole.hasAtLeast(UserRole.ADMIN, UserRole.USER));
        assertTrue(UserRole.hasAtLeast(UserRole.ADMIN, UserRole.STUDENT));
        assertTrue(UserRole.hasAtLeast(UserRole.ADMIN, UserRole.SCHOLAR));
        assertTrue(UserRole.hasAtLeast(UserRole.ADMIN, UserRole.ADMIN));
    }

    @Test
    void hasAtLeast_scholar_canDoStudentAndUserActions() {
        assertTrue(UserRole.hasAtLeast(UserRole.SCHOLAR, UserRole.USER));
        assertTrue(UserRole.hasAtLeast(UserRole.SCHOLAR, UserRole.STUDENT));
        assertTrue(UserRole.hasAtLeast(UserRole.SCHOLAR, UserRole.SCHOLAR));
        assertFalse(UserRole.hasAtLeast(UserRole.SCHOLAR, UserRole.ADMIN));
    }

    @Test
    void hasAtLeast_student_cannotDoScholarOrAdminActions() {
        assertTrue(UserRole.hasAtLeast(UserRole.STUDENT, UserRole.USER));
        assertTrue(UserRole.hasAtLeast(UserRole.STUDENT, UserRole.STUDENT));
        assertFalse(UserRole.hasAtLeast(UserRole.STUDENT, UserRole.SCHOLAR));
        assertFalse(UserRole.hasAtLeast(UserRole.STUDENT, UserRole.ADMIN));
    }

    @Test
    void hasAtLeast_user_onlyHasUserPermissions() {
        assertTrue(UserRole.hasAtLeast(UserRole.USER, UserRole.USER));
        assertFalse(UserRole.hasAtLeast(UserRole.USER, UserRole.STUDENT));
        assertFalse(UserRole.hasAtLeast(UserRole.USER, UserRole.SCHOLAR));
        assertFalse(UserRole.hasAtLeast(UserRole.USER, UserRole.ADMIN));
    }

    @Test
    void hasAtLeast_nullActual_returnsFalse() {
        assertFalse(UserRole.hasAtLeast(null, UserRole.USER));
        assertFalse(UserRole.hasAtLeast(null, UserRole.ADMIN));
    }

    @Test
    void hasAtLeast_unknownActualRole_returnsFalse() {
        assertFalse(UserRole.hasAtLeast("MODERATOR", UserRole.USER));
    }

    @Test
    void hasAtLeast_throws_forInvalidRequired() {
        assertThrows(IllegalArgumentException.class,
            () -> UserRole.hasAtLeast(UserRole.ADMIN, "MODERATOR"));
        assertThrows(IllegalArgumentException.class,
            () -> UserRole.hasAtLeast(UserRole.USER, null));
    }
}
