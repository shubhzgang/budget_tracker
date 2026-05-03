package com.budget.tracker.repository;

import com.budget.tracker.model.User;
import com.budget.tracker.model.UserPreference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserPreferenceRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Test
    void findByUserId_ReturnsPreference() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hash");
        user = entityManager.persistAndFlush(user);

        UserPreference prefs = new UserPreference();
        prefs.setUserId(user.getId());
        prefs.setDefaultAccountId(UUID.randomUUID());
        entityManager.persistAndFlush(prefs);

        Optional<UserPreference> found = userPreferenceRepository.findByUserId(user.getId());

        assertTrue(found.isPresent());
        assertEquals(prefs.getDefaultAccountId(), found.get().getDefaultAccountId());
    }
}
