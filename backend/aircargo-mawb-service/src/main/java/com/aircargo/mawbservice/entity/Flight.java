package com.aircargo.mawbservice.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "flight", schema = "flight")
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "flight_number", length = 20, nullable = false)
    private String flightNumber;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }
}