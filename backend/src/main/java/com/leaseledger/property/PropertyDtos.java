package com.leaseledger.property;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Request and response bodies for the property and unit endpoints. */
public final class PropertyDtos {

    private PropertyDtos() {
    }

    public record PropertyRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 255) String address,
            @NotBlank @Size(max = 80) String city) {
    }

    public record PropertyResponse(Long id, String name, String address, String city) {

        static PropertyResponse from(Property p) {
            return new PropertyResponse(p.getId(), p.getName(), p.getAddress(), p.getCity());
        }
    }

    /** One row of the property list. Filled directly by the query in PropertyRepository. */
    public record PropertySummary(Long id, String name, String city, Long unitCount, Long vacantUnitCount) {
    }

    public record UnitRequest(
            @NotBlank @Size(max = 20) String unitNumber,
            @NotNull @Min(0) @Max(20) Integer bedrooms,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal monthlyRent,
            /** Optional. Defaults to VACANT. OCCUPIED is rejected: only a lease can occupy a unit. */
            UnitStatus status) {
    }

    public record UnitResponse(Long id, String unitNumber, int bedrooms, BigDecimal monthlyRent, UnitStatus status) {

        static UnitResponse from(Unit u) {
            return new UnitResponse(u.getId(), u.getUnitNumber(), u.getBedrooms(), u.getMonthlyRent(), u.getStatus());
        }
    }
}
