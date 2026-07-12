package org.settlementservice.settlementservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.dtos.request.SettlementBankRequest;
import org.settlementservice.settlementservice.dtos.request.SettlementBankUpdateRequest;
import org.settlementservice.settlementservice.dtos.response.SettlementBankResponse;
import org.settlementservice.settlementservice.enums.SettlementBankStatus;
import org.settlementservice.settlementservice.exceptions.DuplicateResourceException;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.SettlementBank;
import org.settlementservice.settlementservice.repositories.SettlementBankRepository;
import org.settlementservice.settlementservice.services.SettlementBankService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementBankServiceImpl implements SettlementBankService {

    private final SettlementBankRepository settlementBankRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public SettlementBankResponse create(SettlementBankRequest request) {
        if (settlementBankRepository.findByName(request.getName()).isPresent()) {
            throw new DuplicateResourceException(
                    "A settlement bank with name - " + request.getName() + " already exists");
        }
        if (settlementBankRepository.findByCode(request.getCode()).isPresent()) {
            throw new DuplicateResourceException(
                    "A settlement bank with code - " + request.getCode() + " already exists");
        }

        SettlementBank bank = new SettlementBank();
        bank.setName(request.getName());
        bank.setCode(request.getCode());
        bank.setStatus(SettlementBankStatus.ACTIVE);

        return toResponse(settlementBankRepository.save(bank));
    }

    @Override
    public SettlementBankResponse getById(Long id) {
        return toResponse(findBankOrThrow(id));
    }

    @Override
    public List<SettlementBankResponse> getAll() {
        return settlementBankRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public SettlementBankResponse update(Long id, SettlementBankUpdateRequest request) {
        SettlementBank bank = findBankOrThrow(id);
        bank.setName(request.getName());
        bank.setStatus(request.getStatus());
        return toResponse(bank);
    }


    private SettlementBank findBankOrThrow(Long id) {
        return settlementBankRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No settlement bank found with id " + id));
    }

    private SettlementBankResponse toResponse(SettlementBank bank) {
        return modelMapper.map(bank, SettlementBankResponse.class);
    }
}
