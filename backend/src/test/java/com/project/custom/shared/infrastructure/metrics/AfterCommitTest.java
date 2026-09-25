package com.project.custom.shared.infrastructure.metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(OutputCaptureExtension.class)
class AfterCommitTest {

    private final AtomicInteger runs = new AtomicInteger();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void withoutTransactionTheActionRunsImmediately() {
        AfterCommit.run(runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }

    @Test
    void withinTransactionTheActionRunsOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(runs::incrementAndGet);
        assertThat(runs).hasValue(0);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(runs).hasValue(1);
    }

    @Test
    void rolledBackTransactionNeverRunsTheAction() {
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(runs::incrementAndGet);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(runs).hasValue(0);
    }

    @Test
    void failingActionIsLoggedAndDoesNotAbortTheCommit(CapturedOutput output) {
        TransactionSynchronizationManager.initSynchronization();
        AfterCommit.run(() -> {
            throw new IllegalStateException("registry down");
        });
        AfterCommit.run(runs::incrementAndGet);

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();

        assertThat(runs).hasValue(1);
        assertThat(output.getAll()).contains("WARN").contains("registry down");
    }
}
