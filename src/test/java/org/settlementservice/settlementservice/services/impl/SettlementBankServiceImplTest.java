package org.settlementservice.settlementservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.dtos.request.SettlementBankRequest;
import org.settlementservice.settlementservice.dtos.request.SettlementBankUpdateRequest;
import org.settlementservice.settlementservice.dtos.response.SettlementBankResponse;
import org.settlementservice.settlementservice.enums.SettlementBankStatus;
import org.settlementservice.settlementservice.exceptions.DuplicateResourceException;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.SettlementBank;
import org.settlementservice.settlementservice.repositories.SettlementBankRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementBankServiceImplTest {

    @Mock
    private SettlementBankRepository settlementBankRepository;

    private SettlementBankServiceImpl settlementBankService;

    @BeforeEach
    void setUp() {
        settlementBankService = new SettlementBankServiceImpl(settlementBankRepository, new ModelMapper());
    }

    private SettlementBankRequest requestFor(String name, String code) {
        SettlementBankRequest request = new SettlementBankRequest();
        request.setName(name);
        request.setCode(code);
        return request;
    }

    @Test
    void create_newBank_savesAsActive() {
        when(settlementBankRepository.findByName("Kuda Bank")).thenReturn(Optional.empty());
        when(settlementBankRepository.findByCode("050")).thenReturn(Optional.empty());
        when(settlementBankRepository.save(any(SettlementBank.class))).thenAnswer(invocation -> {
            SettlementBank bank = invocation.getArgument(0);
            bank.setId(11L);
            return bank;
        });

        SettlementBankResponse response = settlementBankService.create(requestFor("Kuda Bank", "050"));

        assertThat(response.getId()).isEqualTo(11L);
        assertThat(response.getName()).isEqualTo("Kuda Bank");
        assertThat(response.getCode()).isEqualTo("050");
        assertThat(response.getStatus()).isEqualTo(SettlementBankStatus.ACTIVE);
    }

    @Test
    void create_duplicateName_throwsDuplicateResourceException() {
        when(settlementBankRepository.findByName("Guaranty Trust Bank"))
                .thenReturn(Optional.of(new SettlementBank()));

        assertThatThrownBy(() -> settlementBankService.create(requestFor("Guaranty Trust Bank", "999")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Guaranty Trust Bank");

        verify(settlementBankRepository, never()).save(any());
    }

    @Test
    void create_duplicateCode_throwsDuplicateResourceException() {
        when(settlementBankRepository.findByName("Fake GTBank")).thenReturn(Optional.empty());
        when(settlementBankRepository.findByCode("058")).thenReturn(Optional.of(new SettlementBank()));

        assertThatThrownBy(() -> settlementBankService.create(requestFor("Fake GTBank", "058")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("058");

        verify(settlementBankRepository, never()).save(any());
    }

    @Test
    void getById_missingBank_throwsResourceNotFoundException() {
        when(settlementBankRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settlementBankService.getById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_existingBank_changesNameAndStatus() {
        SettlementBank bank = new SettlementBank();
        bank.setId(11L);
        bank.setName("Kuda Bank");
        bank.setCode("050");
        when(settlementBankRepository.findById(11L)).thenReturn(Optional.of(bank));

        SettlementBankUpdateRequest request = new SettlementBankUpdateRequest();
        request.setName("Kuda Microfinance Bank");
        request.setStatus(SettlementBankStatus.INACTIVE);

        SettlementBankResponse response = settlementBankService.update(11L, request);

        assertThat(response.getName()).isEqualTo("Kuda Microfinance Bank");
        assertThat(response.getStatus()).isEqualTo(SettlementBankStatus.INACTIVE);
        assertThat(response.getCode()).isEqualTo("050");
    }
}
