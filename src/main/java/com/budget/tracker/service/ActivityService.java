package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.ActivityItem;
import com.budget.tracker.model.Label;
import com.budget.tracker.payload.response.ActivityResponse;
import com.budget.tracker.repository.ActivityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public Page<ActivityResponse> getActivity(String searchTerm, String type, UUID accountId, OffsetDateTime startDate, OffsetDateTime endDate, Pageable pageable) {
        UUID userId = getCurrentUserId();
        Page<ActivityItem> items = activityRepository.searchActivity(userId, searchTerm, type, accountId, startDate, endDate, pageable);
        populateLabels(items.getContent());
        List<ActivityResponse> responses = new ArrayList<>();
        for (ActivityItem item : items.getContent()) {
            if (searchTerm != null && !searchTerm.isEmpty()) {
                boolean descMatch = item.getDescription() != null && item.getDescription().toLowerCase().contains(searchTerm.toLowerCase());
                boolean catMatch = item.getCategory() != null && item.getCategory().getName() != null && item.getCategory().getName().toLowerCase().contains(searchTerm.toLowerCase());
                boolean labelMatch = item.getLabels() != null && item.getLabels().stream()
                        .anyMatch(l -> l.getName().toLowerCase().contains(searchTerm.toLowerCase()));
                if (!descMatch && !catMatch && !labelMatch) {
                    continue;
                }
            }
            responses.add(mapToResponse(item));
        }
        return new PageImpl<>(responses, pageable, items.getTotalElements());
    }

    public List<ActivityResponse> getActivityForAccount(UUID accountId) {
        UUID userId = getCurrentUserId();
        List<ActivityItem> items = activityRepository.findByAccountId(userId, accountId);
        populateLabels(items);
        return items.stream().map(this::mapToResponse).toList();
    }

    private void populateLabels(List<ActivityItem> items) {
        if (items.isEmpty()) return;

        List<UUID> transactionIds = new ArrayList<>();
        List<UUID> transferIds = new ArrayList<>();

        for (ActivityItem item : items) {
            if (item.getKind() == null) continue;
            if (item.getKind().equals("TRANSACTION")) {
                transactionIds.add(item.getId());
            } else if (item.getKind().equals("TRANSFER")) {
                transferIds.add(item.getId());
            }
        }

        Map<UUID, Set<Label>> labelMap = new HashMap<>();

        if (!transactionIds.isEmpty()) {
            for (Object[] row : activityRepository.findLabelsForTransactions(transactionIds)) {
                UUID activityId = toUuid(row[0]);
                Label label = new Label();
                label.setId(toUuid(row[1]));
                label.setName((String) row[2]);
                label.setDefault((Boolean) row[3]);
                label.setUserId(toUuid(row[4]));
                labelMap.computeIfAbsent(activityId, k -> new HashSet<>()).add(label);
            }
        }

        if (!transferIds.isEmpty()) {
            for (Object[] row : activityRepository.findLabelsForTransfers(transferIds)) {
                UUID activityId = toUuid(row[0]);
                Label label = new Label();
                label.setId(toUuid(row[1]));
                label.setName((String) row[2]);
                label.setDefault((Boolean) row[3]);
                label.setUserId(toUuid(row[4]));
                labelMap.computeIfAbsent(activityId, k -> new HashSet<>()).add(label);
            }
        }

        for (ActivityItem item : items) {
            Set<Label> labels = labelMap.get(item.getId());
            if (labels != null) {
                item.setLabels(labels);
            }
        }
    }

    private UUID toUuid(Object obj) {
        if (obj instanceof UUID u) return u;
        if (obj instanceof byte[] bytes) {
            ByteBuffer bb = ByteBuffer.wrap(bytes);
            long msb = bb.getLong();
            long lsb = bb.getLong();
            return new UUID(msb, lsb);
        }
        if (obj instanceof String s) return UUID.fromString(s);
        throw new ClassCastException("Cannot convert " + obj.getClass() + " to UUID");
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
        response.setLabels(item.getLabels());
        response.setCreatedAt(item.getCreatedAt());
        return response;
    }
}
