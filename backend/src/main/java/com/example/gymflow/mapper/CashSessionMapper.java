package com.example.gymflow.mapper;

import com.example.gymflow.dto.cash.CashSessionResponse;
import com.example.gymflow.dto.payment.PaymentTotals;
import com.example.gymflow.entity.CashSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

/**
 * Maps cash sessions; totals and computed amounts are passed in ({@code null} for a blind view).
 */
@Mapper(componentModel = "spring")
public interface CashSessionMapper {
    @Mapping(target = "id", source = "session.id")
    @Mapping(target = "status", source = "session.status")
    @Mapping(target = "openedAt", source = "session.openedAt")
    @Mapping(target = "openedByName", source = "session.openedBy.account.fullName")
    @Mapping(target = "openingAmount", source = "session.openingAmount")
    @Mapping(target = "closedAt", source = "session.closedAt")
    @Mapping(target = "closedByName", source = "session.closedBy.account.fullName")
    @Mapping(target = "countedCash", source = "session.countedCash")
    @Mapping(target = "notes", source = "session.notes")
    @Mapping(target = "totals", source = "totals")
    @Mapping(target = "expectedCash", source = "expectedCash")
    @Mapping(target = "difference", source = "difference")
    CashSessionResponse toResponse(CashSession session, PaymentTotals totals, BigDecimal expectedCash, BigDecimal difference);
}
