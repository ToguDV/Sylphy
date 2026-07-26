package com.togudv.sylphy.model;

import jakarta.persistence.Embeddable;
import lombok.Data;
import java.time.DayOfWeek;
import java.util.Set;

@Embeddable
@Data
public class RecurrentConfig {
    private Set<Frequency> frequencyType;
    private Integer interval;           // cada X unidades
    private Set<DayOfWeek> daysOfWeek;  // solo para WEEKLY
    private Set<Integer> daysOfMonth;
    private Integer occurrences;
}

enum Frequency {
    MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY
}

