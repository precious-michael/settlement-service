package org.settlementservice.settlementservice.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL) // Don't include any field that is null
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class SettlementServiceResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String error;
    private List<String> details; // If errors details is a list

    public static <T> SettlementServiceResponse<T> success(String message, T data) {
        return new SettlementServiceResponse<>(true, message, data, null, null);
    }

    public static <T> SettlementServiceResponse<T> success(String message) {
        return new SettlementServiceResponse<>(true, message, null, null, null);
    }
}
