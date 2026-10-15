package com.example.gymflow.service;

import com.example.gymflow.dto.membership.MembershipResponse;
import com.example.gymflow.dto.membership.MembershipSaleRequest;

/**
 * Membership sales and lifecycle.
 */
public interface MembershipService {
    /**
     * Sells or renews a membership and registers its payment in the open cash session. Renewals start the day
     * after the latest non-cancelled membership. Replaying the same {@code idempotencyKey} returns the original sale.
     *
     * @param memberId   the member id
     * @param request    the sale
     * @param staffId    the authenticated staff id (receives the payment)
     * @param callerRole the authenticated role (only OWNER/ADMIN may change the price)
     * @return the membership
     * @throws com.example.gymflow.exception.BusinessException when there is no open cash session, or the member or plan is inactive
     */
    MembershipResponse sellMembership(Long memberId, MembershipSaleRequest request, Long staffId, String callerRole);

    /**
     * Freezes an active membership from today.
     */
    MembershipResponse freezeMembershipById(Long membershipId);

    /**
     * Unfreezes a membership, extending it (and any queued renewals) by the frozen days.
     */
    MembershipResponse unfreezeMembershipById(Long membershipId);

    MembershipResponse cancelMembershipById(Long membershipId);
}
