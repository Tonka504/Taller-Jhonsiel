package com.taller.repuestos.exception;

/** Error esperable (permiso denegado, dato inválido, regla incumplida) con un mensaje listo para mostrar al usuario. */
public class ReglaNegocioException extends Exception {
    public ReglaNegocioException(String mensaje) { super(mensaje); }
}
