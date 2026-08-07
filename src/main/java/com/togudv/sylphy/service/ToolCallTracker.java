package com.togudv.sylphy.service;

/**
 * Marca por hilo si una herramienta fue ejecutada durante una llamada a
 * AIService.generate. Sirve para no reintentar una generacion cuando el
 * fallo del proveedor ocurrio DESPUES de ejecutar una herramienta con
 * efectos secundarios (p. ej. createReminder): reintentar re-ejecutaria
 * la herramienta y duplicaria el efecto.
 */
final class ToolCallTracker {

    private static final ThreadLocal<Boolean> TOOL_EXECUTED = ThreadLocal.withInitial(() -> false);

    private ToolCallTracker() {
    }

    static void markToolExecuted() {
        TOOL_EXECUTED.set(true);
    }

    static void reset() {
        TOOL_EXECUTED.set(false);
    }

    static boolean wasToolExecuted() {
        return TOOL_EXECUTED.get();
    }

    static void clear() {
        TOOL_EXECUTED.remove();
    }
}
