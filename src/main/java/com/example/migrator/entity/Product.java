
package com.example.migrator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import java.math.BigDecimal;

@Entity
@Table(name = "product")
public class Product {
    @Id
    private Long id;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "price", precision = 13, scale = 2)
    private BigDecimal price;

    @Column(name = "stock")
    private Integer stock;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}
