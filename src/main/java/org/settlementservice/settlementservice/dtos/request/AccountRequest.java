package org.settlementservice.settlementservice.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountRequest {
        @NotBlank(message = "Account name is required")
        private String name;

        @NotBlank(message = "Account number is required")
        @Pattern(regexp = "^[0-9]{10,34}$", message = "Account Number must be a numeric value between 10 and 34 digits long")
        private String accountNumber;

        @NotNull(message = "Settlement bank is required")
        private Long bankId;
}
