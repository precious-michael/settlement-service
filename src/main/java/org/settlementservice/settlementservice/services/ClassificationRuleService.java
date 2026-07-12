package org.settlementservice.settlementservice.services;

import org.settlementservice.settlementservice.dtos.request.ClassificationRuleRequest;
import org.settlementservice.settlementservice.dtos.response.ClassificationRuleResponse;

import java.util.List;

public interface ClassificationRuleService {

    ClassificationRuleResponse create(ClassificationRuleRequest request);

    ClassificationRuleResponse getById(Long id);

    List<ClassificationRuleResponse> getAll();

    ClassificationRuleResponse update(Long id, ClassificationRuleRequest request);

    void delete(Long id);
}
