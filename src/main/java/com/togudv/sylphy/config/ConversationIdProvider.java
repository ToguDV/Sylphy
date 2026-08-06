package com.togudv.sylphy.config;

/**
 * Resuelve el identificador de la conversacion del asistente. Todos los
 * canales (Telegram hoy, REST/web manana) comparten el mismo identificador,
 * de modo que el historial de chat es comun para el unico owner de la
 * instancia. Si algun dia hubiera mas de un owner, esta es la interfaz
 * que se refactoriza: la entidad y los servicios no cambian.
 */
public interface ConversationIdProvider {

    String getConversationId();
}
