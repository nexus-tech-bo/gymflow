package com.example.gymflow.mapper;

import com.example.gymflow.dto.payment.PaymentResponse;
import com.example.gymflow.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Maps payments.
 */
@Mapper(componentModel = "spring")
public interface PaymentMapper {
    @Mapping(target = "memberId", source = "member.id")
    @Mapping(target = "memberName", source = "member.fullName")
    @Mapping(target = "receivedByName", source = "receivedBy.account.fullName")
    PaymentResponse toResponse(Payment payment);

    List<PaymentResponse> toResponseList(List<Payment> payments);
}
