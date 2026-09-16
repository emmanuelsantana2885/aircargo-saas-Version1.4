package com.aircargo.flightservice.entity;

import com.aircargo.common.entity.Airline;
import com.aircargo.common.entity.AircraftType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "flight", schema = "flight")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airline_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Airline airline;

    @Column(name = "flight_number", nullable = false, length = 20)
    private String flightNumber;

    @Column(name = "origin", nullable = false, columnDefinition = "bpchar(3)")
    private String origin;

    @Column(name = "destination", nullable = false, columnDefinition = "bpchar(3)")
    private String destination;

    @Column(name = "aircraft_reg", length = 20)
    private String aircraftReg;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "aircraft_type", columnDefinition = "aircraft_type")
    private AircraftType aircraftType;

    @Column(name = "flight_date", nullable = false)
    private LocalDate flightDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "flight_status")
    private FlightStatus status = FlightStatus.SCHEDULED;

    @Column(name = "max_payload_kg", precision = 10, scale = 2)
    private BigDecimal maxPayloadKg;

    @Column(name = "total_positions")
    private Integer totalPositions;

    @Column(name = "notes")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Flight(Airline airline, String flightNumber, String origin,
                  String destination, String aircraftReg, LocalDate flightDate) {
        this.airline = airline;
        this.flightNumber = flightNumber;
        this.origin = origin;
        this.destination = destination;
        this.aircraftReg = aircraftReg;
        this.flightDate = flightDate;
        this.status = FlightStatus.SCHEDULED;
    }
}
