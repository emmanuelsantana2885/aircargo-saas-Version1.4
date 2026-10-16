package com.aircargo.loadplanningservice.dto;

/**
 * Solicitud para recuperar un load planning ya despachado.
 * Solo permitido ante una excepcion operativa (problema de vuelo o desviacion
 * de la operacion); tanto el tipo como el motivo son obligatorios.
 */
public record RecoverLoadPlanRequest(String exceptionType, String reason) {}
