package com.aircargo.auditreportsservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "uld_awb")
@Getter
@Setter
@NoArgsConstructor
public class UldAwbEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "uld_id", nullable = false)
    private UUID uldId;

    @Column(name = "mawb_id")
    private UUID mawbId;

    @Column(name = "destination", length = 3)
    private String destination;

    @Column(name = "pieces")
    private Integer pieces;
}
