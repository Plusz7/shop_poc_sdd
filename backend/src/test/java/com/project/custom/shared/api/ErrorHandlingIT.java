package com.project.custom.shared.api;

import com.project.custom.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test of the foundation: the context starts against a real SQL Server, and an unknown API path
 * produces an RFC 9457 problem with a contract code.
 */
class ErrorHandlingIT extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GlobalExceptionHandler exceptionHandler;

    @Test
    void unknownApiPathReturnsProblemWithNotFoundCode() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists());
    }

    /** A stale write from another tab (optimistic locking) is reported as {@code 409 CONCURRENCY_CONFLICT}. */
    @Test
    void optimisticLockingFailureIsAConcurrencyConflict() {
        ResponseEntity<ProblemDetail> response = exceptionHandler.handleConcurrencyConflict(
                new ObjectOptimisticLockingFailureException("CartJpaEntity", UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "CONCURRENCY_CONFLICT");
        assertThat(response.getBody().getDetail()).isNotBlank().doesNotStartWith("error.");
    }
}
