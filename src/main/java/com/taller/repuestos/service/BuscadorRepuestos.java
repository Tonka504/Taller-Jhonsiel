package com.taller.repuestos.service;

import com.taller.repuestos.model.Compatibilidad;
import com.taller.repuestos.model.Repuesto;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Búsqueda inteligente de repuestos. Entiende frases como "Toyota Hilux 2007 filtro":
 * cada palabra puede coincidir (en parte, sin importar mayúsculas ni acentos) con datos del producto
 * o con UN mismo vehículo compatible, y los años (4 cifras) tienen que caer dentro del rango de ese vehículo.
 */
public final class BuscadorRepuestos {

    private BuscadorRepuestos() {}

    /** Una búsqueda ya interpretada. Se crea una vez por cada vez que el usuario escribe. */
    public static final class Consulta {
        private final List<String> palabras = new ArrayList<>();
        private final List<Integer> anios = new ArrayList<>();
        private String textoOriginal = "";
        // Filtros por vehículo (de los combos Marca → Modelo → Año → Motor); vacíos no restringen
        private String marcaVehiculo = "";
        private String modelo = "";
        private Integer anio;
        private String motor = "";

        private Consulta() {}

        /** Interpreta lo que escribió el usuario. */
        public static Consulta de(String texto) {
            Consulta c = new Consulta();
            c.textoOriginal = texto == null ? "" : texto.trim();
            for (String token : c.textoOriginal.split("\\s+")) {
                String n = normalizar(token);
                if (n.isEmpty()) continue;
                Integer anio = comoAnio(n);
                if (anio != null) c.anios.add(anio);
                else c.palabras.add(n);
            }
            return c;
        }

        /** Agrega filtros de vehículo elegidos en los combos (cualquiera puede venir vacío). */
        public Consulta conVehiculo(String marca, String modelo, Integer anio, String motor) {
            this.marcaVehiculo = normalizar(marca);
            this.modelo = normalizar(modelo);
            this.anio = anio;
            this.motor = normalizar(motor);
            return this;
        }

        public boolean estaVacia() {
            return palabras.isEmpty() && anios.isEmpty() && marcaVehiculo.isEmpty() && modelo.isEmpty()
                    && anio == null && motor.isEmpty();
        }

        private boolean hayFiltroVehiculo() {
            return !marcaVehiculo.isEmpty() || !modelo.isEmpty() || anio != null || !motor.isEmpty();
        }

        /** ¿El producto cumple la búsqueda? Una búsqueda vacía los acepta todos. */
        public boolean coincide(Repuesto r, List<Compatibilidad> compat) {
            if (estaVacia()) return true;
            List<Compatibilidad> filas = compat == null ? List.of() : compat;
            String textoProducto = textoDe(r);

            // Sin vehículos compatibles: solo cuentan las palabras del producto (y no se puede filtrar por vehículo ni año)
            if (filas.isEmpty()) {
                if (hayFiltroVehiculo()) return false;
                for (String p : palabras) if (!textoProducto.contains(p)) return false;
                for (Integer a : anios) if (!textoProducto.contains(String.valueOf(a))) return false;
                return true;
            }

            // Con vehículos: tiene que haber UN vehículo que, junto con el producto, cumpla todo
            for (Compatibilidad c : filas) {
                if (cumpleFiltroVehiculo(c) && cumplePalabras(textoProducto, c) && cumpleAnios(textoProducto, c)) {
                    return true;
                }
            }
            // Si no se pidió nada de vehículos, el producto también vale solo por sus propios datos
            if (!hayFiltroVehiculo() && anios.isEmpty()) {
                for (String p : palabras) if (!textoProducto.contains(p)) return false;
                return true;
            }
            return false;
        }

        private boolean cumpleFiltroVehiculo(Compatibilidad c) {
            if (!marcaVehiculo.isEmpty() && !normalizar(c.getMarcaVehiculo()).equals(marcaVehiculo)) return false;
            if (!modelo.isEmpty() && !normalizar(c.getModelo()).equals(modelo)) return false;
            if (anio != null && !c.cubreAnio(anio)) return false;
            // Un vehículo sin motor indicado sirve para cualquier motor (igual que un año vacío sirve para cualquier año)
            String motorDelVehiculo = normalizar(c.getMotor());
            if (!motor.isEmpty() && !motorDelVehiculo.isEmpty() && !motorDelVehiculo.contains(motor)) return false;
            return true;
        }

        private boolean cumplePalabras(String textoProducto, Compatibilidad c) {
            String textoVehiculo = textoDe(c);
            for (String p : palabras) {
                if (!textoProducto.contains(p) && !textoVehiculo.contains(p)) return false;
            }
            return true;
        }

        private boolean cumpleAnios(String textoProducto, Compatibilidad c) {
            for (Integer a : anios) {
                if (!c.cubreAnio(a) && !textoProducto.contains(String.valueOf(a))) return false;
            }
            return true;
        }

        /** Para ordenar: sube lo que coincide exacto con un código y lo que tiene la palabra en el nombre. */
        int puntaje(Repuesto r) {
            int puntos = 0;
            String codigo = normalizar(r.getCodigo());
            String barras = normalizar(r.getCodigoBarras());
            String desc = normalizar(r.getDescripcion());
            for (String p : palabras) {
                if (codigo.equals(p) || barras.equals(p)) puntos += 1000;
                else if (desc.startsWith(p)) puntos += 30;
                else if (desc.contains(p)) puntos += 20;
                else if (codigo.contains(p) || barras.contains(p)) puntos += 10;
                else puntos += 3;
            }
            if (!textoOriginal.isEmpty() && (codigo.equals(normalizar(textoOriginal)) || barras.equals(normalizar(textoOriginal)))) {
                puntos += 1000;
            }
            if (r.getStock() > 0) puntos += 1;
            return puntos;
        }
    }

    /** Busca y devuelve los productos que cumplen la consulta, los más relevantes primero. */
    public static List<Repuesto> buscar(List<Repuesto> productos, Map<Integer, List<Compatibilidad>> compatPorProducto,
                                        Consulta consulta) {
        List<Repuesto> resultado = new ArrayList<>();
        for (Repuesto r : productos) {
            if (consulta.coincide(r, compatPorProducto.get(r.getId()))) resultado.add(r);
        }
        resultado.sort(Comparator
                .comparingInt((Repuesto r) -> consulta.puntaje(r)).reversed()
                .thenComparing(r -> normalizar(r.getDescripcion())));
        return resultado;
    }

    /** Resumen de los vehículos compatibles para mostrar en una celda (máximo 3, el resto como "+N más"). */
    public static String resumenCompatibilidad(List<Compatibilidad> compat) {
        if (compat == null || compat.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int mostrados = Math.min(3, compat.size());
        for (int i = 0; i < mostrados; i++) {
            if (i > 0) sb.append("; ");
            sb.append(compat.get(i).getResumen());
        }
        if (compat.size() > mostrados) sb.append("; +").append(compat.size() - mostrados).append(" más");
        return sb.toString();
    }

    // ------------------------------------------------------------------

    private static String textoDe(Repuesto r) {
        return normalizar(String.join(" ", nz(r.getCodigo()), nz(r.getCodigoBarras()), nz(r.getDescripcion()),
                nz(r.getMarca()), nz(r.getCategoria()), nz(r.getAplicacion()), nz(r.getUbicacion()), nz(r.getProveedor())));
    }

    private static String textoDe(Compatibilidad c) {
        return normalizar(String.join(" ", nz(c.getMarcaVehiculo()), nz(c.getModelo()), nz(c.getMotor()),
                nz(c.getCombustible()), nz(c.getTransmision()), nz(c.getObservaciones())));
    }

    /** Minúsculas y sin acentos, para comparar sin preocuparse por cómo se escribió. */
    static String normalizar(String s) {
        if (s == null) return "";
        String sinAcentos = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinAcentos.toLowerCase(Locale.ROOT).trim();
    }

    private static Integer comoAnio(String token) {
        if (token.length() != 4) return null;
        try {
            int v = Integer.parseInt(token);
            return v >= 1950 && v <= 2100 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
