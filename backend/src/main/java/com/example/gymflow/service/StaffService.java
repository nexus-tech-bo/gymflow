package com.example.gymflow.service;

import com.example.gymflow.dto.auth.MeResponse;
import com.example.gymflow.dto.staff.StaffRequest;
import com.example.gymflow.dto.staff.StaffResponse;
import com.example.gymflow.dto.staff.StaffUpdateRequest;

import java.util.List;

/**
 * Staff users of the current gym.
 */
public interface StaffService {
    /**
     * Returns the authenticated user.
     *
     * @param staffId the authenticated staff id
     * @return the user and its gym
     */
    MeResponse findMe(Long staffId);

    List<StaffResponse> findAllStaff();

    /**
     * Creates a staff user. OWNER cannot be created; ADMIN may only create RECEPTIONIST.
     *
     * @param request    the new user
     * @param callerId   the authenticated staff id
     * @param callerRole the authenticated role
     * @return the created user
     */
    StaffResponse createStaff(StaffRequest request, Long callerId, String callerRole);

    /**
     * Partially updates a staff user. ADMIN may only manage RECEPTIONIST; nobody may change their own
     * role or deactivate themselves; the OWNER cannot be demoted or deactivated.
     *
     * @param staffId    the target staff id
     * @param request    the fields to change
     * @param callerId   the authenticated staff id
     * @param callerRole the authenticated role
     * @return the updated user
     */
    StaffResponse updateStaffById(Long staffId, StaffUpdateRequest request, Long callerId, String callerRole);
}
