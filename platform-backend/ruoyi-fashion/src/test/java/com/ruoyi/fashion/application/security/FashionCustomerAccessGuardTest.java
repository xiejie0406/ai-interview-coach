package com.ruoyi.fashion.application.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

class FashionCustomerAccessGuardTest {
    private final FashionCustomerAccessGuard guard = new FashionCustomerAccessGuard(new ObjectMapper());

    @Test
    void allowsAdministratorOwnerAndPersistedCollaborator() {
        assertDoesNotThrow(() -> guard.requireUser(10, "[]", 99, true));
        assertDoesNotThrow(() -> guard.requireUser(10, "[]", 10, false));
        assertDoesNotThrow(() -> guard.requireUser(10, "[11,12]", 12, false));
    }

    @Test
    void rejectsUnrelatedUserAndMalformedScope() {
        assertThrows(ServiceException.class,
                () -> guard.requireUser(10, "[11,12]", 13, false));
        assertThrows(ServiceException.class,
                () -> guard.requireUser(10, "{\"user\":13}", 13, false));
    }
}
