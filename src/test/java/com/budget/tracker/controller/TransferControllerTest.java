package com.budget.tracker.controller;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.Transfer;
import com.budget.tracker.payload.request.TransferRequest;
import com.budget.tracker.security.UserDetailsImpl;
import com.budget.tracker.service.TransferService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransferService transferService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userDetails = new UserDetailsImpl(userId, "test@test.com", "password");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        AuthContext.setUserId(userId);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateTransfer() throws Exception {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(UUID.randomUUID());
        request.setToAccountId(UUID.randomUUID());
        request.setFromAmount(new BigDecimal("100.00"));
        request.setAdjustment(new BigDecimal("10.00"));
        request.setTransactionDate(OffsetDateTime.now());
        request.setDescription("Atm withdrawal");

        Transfer saved = new Transfer();
        saved.setId(UUID.randomUUID());
        saved.setFromAmount(new BigDecimal("100.00"));
        saved.setToAmount(new BigDecimal("110.00"));
        saved.setAdjustment(new BigDecimal("10.00"));
        saved.setDescription("Atm withdrawal");
        saved.setTransactionDate(request.getTransactionDate());

        when(transferService.createTransfer(any(TransferRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/v1/transfers")
                .with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromAmount").value(100.00))
                .andExpect(jsonPath("$.toAmount").value(110.00))
                .andExpect(jsonPath("$.adjustment").value(10.00))
                .andExpect(jsonPath("$.description").value("Atm withdrawal"));
    }

    @Test
    void shouldGetAllTransfers() throws Exception {
        Transfer t = new Transfer();
        t.setId(UUID.randomUUID());
        t.setDescription("Salary transfer");
        t.setFromAmount(new BigDecimal("100.00"));
        t.setToAmount(new BigDecimal("100.00"));
        t.setAdjustment(BigDecimal.ZERO);

        when(transferService.getAllTransfers(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(t)));

        mockMvc.perform(get("/api/v1/transfers")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("Salary transfer"));
    }

    @Test
    void shouldGetTransferById() throws Exception {
        UUID id = UUID.randomUUID();
        Transfer t = new Transfer();
        t.setId(id);
        t.setDescription("Single transfer");
        t.setFromAmount(new BigDecimal("100.00"));
        t.setToAmount(new BigDecimal("100.00"));
        t.setAdjustment(BigDecimal.ZERO);

        when(transferService.getTransferById(id)).thenReturn(t);

        mockMvc.perform(get("/api/v1/transfers/" + id)
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Single transfer"));
    }

    @Test
    void shouldUpdateTransfer() throws Exception {
        UUID id = UUID.randomUUID();
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(UUID.randomUUID());
        request.setToAccountId(UUID.randomUUID());
        request.setFromAmount(new BigDecimal("150.00"));
        request.setAdjustment(new BigDecimal("0.00"));
        request.setTransactionDate(OffsetDateTime.now());
        request.setDescription("Updated description");

        Transfer updated = new Transfer();
        updated.setId(id);
        updated.setFromAmount(new BigDecimal("150.00"));
        updated.setToAmount(new BigDecimal("150.00"));
        updated.setAdjustment(BigDecimal.ZERO);
        updated.setDescription("Updated description");

        when(transferService.updateTransfer(eq(id), any(TransferRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/transfers/" + id)
                .with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromAmount").value(150.00))
                .andExpect(jsonPath("$.description").value("Updated description"));
    }

    @Test
    void shouldDeleteTransfer() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/transfers/" + id)
                .with(user(userDetails)))
                .andExpect(status().isNoContent());
    }
}
