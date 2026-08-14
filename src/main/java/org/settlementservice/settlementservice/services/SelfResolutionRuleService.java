package org.settlementservice.settlementservice.services;

import org.settlementservice.settlementservice.dtos.request.SelfResolutionRuleRequest;
import org.settlementservice.settlementservice.dtos.response.SelfResolutionRuleResponse;

import java.util.List;

public interface SelfResolutionRuleService {

    List<SelfResolutionRuleResponse> listAll();

    SelfResolutionRuleResponse create(SelfResolutionRuleRequest request);

    SelfResolutionRuleResponse update(Long id, SelfResolutionRuleRequest request);

    void setActive(Long id, boolean active);

    void delete(Long id);
}
