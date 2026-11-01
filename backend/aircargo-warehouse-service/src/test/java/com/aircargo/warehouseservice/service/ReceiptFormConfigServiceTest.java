package com.aircargo.warehouseservice.service;

import com.aircargo.warehouseservice.dto.ReceiptFormConfigDTO;
import com.aircargo.warehouseservice.entity.ReceiptFormConfig;
import com.aircargo.warehouseservice.repository.ReceiptFormConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The optional receipt functions are toggled one at a time from the receipt
 * view, so a PUT that carries a single flag must leave the other one alone.
 */
@ExtendWith(MockitoExtension.class)
class ReceiptFormConfigServiceTest {

    @Mock
    private ReceiptFormConfigRepository repository;

    @InjectMocks
    private ReceiptFormConfigService service;

    private static ReceiptFormConfig existing(boolean seal, boolean dock) {
        ReceiptFormConfig config = new ReceiptFormConfig();
        config.setId(UUID.randomUUID());
        config.setSealNumberEnabled(seal);
        config.setDockBayEnabled(dock);
        return config;
    }

    private static ReceiptFormConfigDTO partial(Boolean seal, Boolean dock) {
        ReceiptFormConfigDTO dto = new ReceiptFormConfigDTO();
        dto.setSealNumberEnabled(seal);
        dto.setDockBayEnabled(dock);
        return dto;
    }

    @Test
    void save_singleFunctionToggle_keepsTheOtherFunctionUntouched() {
        when(repository.findFirstByOrderByUpdatedAtAsc()).thenReturn(Optional.of(existing(false, true)));
        when(repository.save(any(ReceiptFormConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        ReceiptFormConfigDTO saved = service.save(partial(true, null), UUID.randomUUID());

        assertTrue(saved.getSealNumberEnabled(), "the toggled function is enabled");
        assertTrue(saved.getDockBayEnabled(), "the other function keeps its previous value");
    }

    @Test
    void save_canDisableOneFunctionWithoutEnablingTheOther() {
        when(repository.findFirstByOrderByUpdatedAtAsc()).thenReturn(Optional.of(existing(true, true)));
        when(repository.save(any(ReceiptFormConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        ReceiptFormConfigDTO saved = service.save(partial(null, false), UUID.randomUUID());

        assertTrue(saved.getSealNumberEnabled());
        assertFalse(saved.getDockBayEnabled());
    }

    @Test
    void get_whenNoRowExists_createsItWithEveryFunctionDisabled() {
        when(repository.findFirstByOrderByUpdatedAtAsc()).thenReturn(Optional.empty());
        when(repository.save(any(ReceiptFormConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        ReceiptFormConfigDTO config = service.get();

        assertFalse(config.getSealNumberEnabled());
        assertFalse(config.getDockBayEnabled());
    }

    @Test
    void save_partialPayloadNeverNullsAnExistingFunction() {
        ReceiptFormConfig config = existing(true, false);
        when(repository.findFirstByOrderByUpdatedAtAsc()).thenReturn(Optional.of(config));
        when(repository.save(any(ReceiptFormConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID by = UUID.randomUUID();
        ReceiptFormConfigDTO saved = service.save(partial(null, null), by);

        assertTrue(saved.getSealNumberEnabled());
        assertFalse(saved.getDockBayEnabled());
        assertNotEquals(null, config.getUpdatedBy());
    }
}
