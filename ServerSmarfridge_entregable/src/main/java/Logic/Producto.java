package logic;

public class Producto {
    private String rfidTag;
    private String nombre;
    private char nutriScore;
    private int plazoCaducidad; // días en que caduca el producto

    // Constructor
    public Producto(String rfidTag, String nombre, char nutriScore, int plazoCaducidad) {
        this.rfidTag = rfidTag;
        this.nombre = nombre;
        this.nutriScore = nutriScore;
        this.plazoCaducidad = plazoCaducidad;
    }

    // Getters y Setters
    public String getRfidTag() {
        return rfidTag;
    }

    public String getNombre() {
        return nombre;
    }

    public char getNutriScore() {
        return nutriScore;
    }

    public int getPlazoCaducidad() {
        return plazoCaducidad;
    }
}
