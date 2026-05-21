package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.ActivityItem;
import com.budget.tracker.payload.response.ActivityResponse;
import com.budget.tracker.repository.ActivityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;

    public ActivityService(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    private UUID getCurrentUserId() {
        UUID userId = AuthContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("No authenticated user found in context");
        }
        return userId;
    }

    public Page<ActivityResponse> getActivity(String searchTerm, String type, OffsetDateTime startDate, OffsetDateTime endDate, Pageable pageable) {
        UUID userId = getCurrentUserId();
        Page<ActivityItem> items = activityRepository.searchActivity(userId, searchTerm, type, startDate, endDate, pageable);
        return items.map(this::mapToResponse);
    }

    public List<ActivityResponse> getActivityForAccount(UUID accountId) {
        UUID userId = getCurrentUserId();
        List<ActivityItem> items = activityRepository.findByAccountId(userId, accountId);
        return items.stream().map(this::mapToResponse).toList();
    }

    private ActivityResponse mapToResponse(ActivityItem item) {
        ActivityResponse response = new ActivityResponse();
        response.setId(item.getId());
        response.setKind(item.getKind());
        response.setType(item.getType());
        response.setAmount(item.getAmount());
        response.setFromAmount(item.getFromAmount());
        response.setToAmount(item.getToAmount());
        response.setAdjustment(item.getAdjustment());
        response.setDescription(item.getDescription());
        response.setTransactionDate(item.getTransactionDate());
        response.setAccount(item.getAccount());
        response.setToAccount(item.getToAccount());
        response.setCategory(item.getCategory());
        response.setLabel(item.getLabel());
        response.setCreatedAt(item.getCreatedAt());
        return response;
    }
}
