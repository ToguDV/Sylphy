package com.togudv.sylphy.service.conversation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemoryConsolidationSchedulerTest {

    @Mock
    MemoryConsolidationService consolidationService;

    @Test
    void eachJobDelegatesToService() {
        MemoryConsolidationScheduler scheduler = new MemoryConsolidationScheduler(consolidationService);

        scheduler.daily();
        scheduler.weekly();
        scheduler.monthly();
        scheduler.annual();

        verify(consolidationService).consolidateDaily();
        verify(consolidationService).consolidateWeekly();
        verify(consolidationService).consolidateMonthly();
        verify(consolidationService).consolidateAnnual();
    }

    @Test
    void jobFailureIsLoggedAndDoesNotPropagate() {
        doThrow(new IllegalStateException("boom")).when(consolidationService).consolidateDaily();
        MemoryConsolidationScheduler scheduler = new MemoryConsolidationScheduler(consolidationService);

        assertDoesNotThrow(scheduler::daily);
    }
}
