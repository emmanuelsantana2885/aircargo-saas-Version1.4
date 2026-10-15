package com.aircargo.loadplanningservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class LoadPlanningDTO {

    private UUID flightId;
    private String flightNumber;
    private String origin;
    private String destination;
    private String aircraftReg;
    private String aircraftType;
    private LocalDate flightDate;
    private Integer totalPositions;
    private BigDecimal maxPayloadKg;
    private List<LoadPlanningUldDTO> ulds;
    private String airlineName;
    private String airlineCode;
    private WeightBalanceDTO weightBalance;

    public LoadPlanningDTO() {}

    public UUID getFlightId() { return flightId; }
    public void setFlightId(UUID flightId) { this.flightId = flightId; }
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getAircraftReg() { return aircraftReg; }
    public void setAircraftReg(String aircraftReg) { this.aircraftReg = aircraftReg; }
    public String getAircraftType() { return aircraftType; }
    public void setAircraftType(String aircraftType) { this.aircraftType = aircraftType; }
    public LocalDate getFlightDate() { return flightDate; }
    public void setFlightDate(LocalDate flightDate) { this.flightDate = flightDate; }
    public Integer getTotalPositions() { return totalPositions; }
    public void setTotalPositions(Integer totalPositions) { this.totalPositions = totalPositions; }
    public BigDecimal getMaxPayloadKg() { return maxPayloadKg; }
    public void setMaxPayloadKg(BigDecimal maxPayloadKg) { this.maxPayloadKg = maxPayloadKg; }
    public List<LoadPlanningUldDTO> getUlds() { return ulds; }
    public void setUlds(List<LoadPlanningUldDTO> ulds) { this.ulds = ulds; }
    public String getAirlineName() { return airlineName; }
    public void setAirlineName(String airlineName) { this.airlineName = airlineName; }
    public String getAirlineCode() { return airlineCode; }
    public void setAirlineCode(String airlineCode) { this.airlineCode = airlineCode; }
    public WeightBalanceDTO getWeightBalance() { return weightBalance; }
    public void setWeightBalance(WeightBalanceDTO weightBalance) { this.weightBalance = weightBalance; }
}
