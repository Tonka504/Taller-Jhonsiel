package com.taller.repuestos.model;

/** Entrada o salida manual de dinero en una caja abierta (las ventas no se duplican aquí). */
public class MovimientoCaja {

    public enum Tipo {
        ENTRADA("Entrada de efectivo", true, true),
        RETIRO("Retiro de efectivo", false, true),
        GASTO("Gasto", false, true),
        /** Reembolso en efectivo de una devolución: lo crea el sistema, no se registra a mano. */
        DEVOLUCION("Devolución en efectivo", false, false),
        /** Pago en efectivo a un proveedor desde el módulo de compras: lo crea el sistema. Cuenta como gasto. */
        PAGO_PROVEEDOR("Pago a proveedor", false, false);

        private final String etiqueta;
        private final boolean ingreso;
        private final boolean manual;

        Tipo(String etiqueta, boolean ingreso, boolean manual) {
            this.etiqueta = etiqueta;
            this.ingreso = ingreso;
            this.manual = manual;
        }

        /** true si una persona puede registrarlo desde la pantalla de caja. */
        public boolean isManual() { return manual; }

        public String getEtiqueta() { return etiqueta; }
        /** true si suma al efectivo de la caja, false si resta. */
        public boolean isIngreso() { return ingreso; }

        @Override
        public String toString() { return etiqueta; }
    }

    private int id;
    private int cajaId;
    private String fecha;
    private String usuario;
    private String tipo;
    private double monto;
    private String motivo;
    private String documento;

    public MovimientoCaja(int id, int cajaId, String fecha, String usuario, String tipo,
                          double monto, String motivo, String documento) {
        this.id = id;
        this.cajaId = cajaId;
        this.fecha = fecha;
        this.usuario = usuario;
        this.tipo = tipo;
        this.monto = monto;
        this.motivo = motivo;
        this.documento = documento;
    }

    public int getId() { return id; }
    public int getCajaId() { return cajaId; }
    public String getFecha() { return fecha; }
    public String getUsuario() { return usuario; }
    public String getTipo() { return tipo; }
    public double getMonto() { return monto; }
    public String getMotivo() { return motivo; }
    public String getDocumento() { return documento; }

    /** Texto legible del tipo, para la tabla. */
    public String getTipoEtiqueta() {
        try {
            return Tipo.valueOf(tipo).getEtiqueta();
        } catch (IllegalArgumentException e) {
            return tipo;
        }
    }
}
