package com.ecommerce.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingAddress {

    @NotBlank
    @Column(name = "shipping_full_name", nullable = false)
    private String fullName;

    @NotBlank
    @Column(name = "shipping_line1", nullable = false)
    private String addressLine1;

    @Column(name = "shipping_line2")
    private String addressLine2;

    @NotBlank
    @Column(name = "shipping_city", nullable = false)
    private String city;

    @NotBlank
    @Column(name = "shipping_state", nullable = false)
    private String state;

    @NotBlank
    @Column(name = "shipping_postal_code", nullable = false, length = 20)
    private String postalCode;

    @NotBlank
    @Column(name = "shipping_country", nullable = false, length = 3)
    private String country;

    @Column(name = "shipping_phone", length = 20)
    private String phone;
}
