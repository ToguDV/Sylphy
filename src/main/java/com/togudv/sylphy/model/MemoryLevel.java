package com.togudv.sylphy.model;

/**
 * Niveles de la memoria episodica jerarquica. Cada nivel consolida el inferior
 * y, salvo ANNUAL, se pliega en el nivel superior.
 */
public enum MemoryLevel {
    WINDOW,
    DAILY,
    WEEKLY,
    MONTHLY,
    ANNUAL
}
