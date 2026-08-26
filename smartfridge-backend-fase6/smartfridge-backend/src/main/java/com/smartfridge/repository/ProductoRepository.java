package com.smartfridge.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartfridge.model.Producto;

/** Acceso a la tabla "producto" (catálogo, PK natural = rfid_tag). */
public interface ProductoRepository extends JpaRepository<Producto, String> {
}
