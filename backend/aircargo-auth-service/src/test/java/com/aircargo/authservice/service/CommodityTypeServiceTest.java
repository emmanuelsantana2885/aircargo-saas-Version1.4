package com.aircargo.authservice.service;

import com.aircargo.authservice.entity.CommodityTypeEntity;
import com.aircargo.authservice.repository.CommodityTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regresión del bug: {@code resetToDefaults()} hacía {@code repository.deleteAll()}
 * y luego insertaba 25 códigos propios. Eso BORRABA los códigos del catálogo que
 * no estaban en esa lista (HIGH_VALUES, CIGARETTES, SMALL_PACKAGES, LIVE_PLANTS,
 * COMAT, EMPTY_PALLET, RED_TAG…), que pueden estar referenciados por
 * {@code mawb.commodity_type} / {@code booking}, dejándolos sin catálogo y
 * rompiendo los selects de commodity en toda la UI.
 */
class CommodityTypeServiceTest {

    private CommodityTypeRepository repository;
    private CommodityTypeService service;

    @BeforeEach
    void setUp() {
        repository = mock(CommodityTypeRepository.class);
        service = new CommodityTypeService(repository);
        when(repository.save(any(CommodityTypeEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    private CommodityTypeEntity row(String code, String label, String description) {
        return CommodityTypeEntity.builder()
            .id(UUID.randomUUID()).code(code).label(label).description(description)
            .color("#000000").sortOrder(99).isActive(false).build();
    }

    @Test
    @DisplayName("resetToDefaults NO borra el catálogo: nunca llama a deleteAll()")
    void resetToDefaults_neverDeletesCatalog() {
        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());

        service.resetToDefaults();

        verify(repository, never()).deleteAll();
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteAllInBatch();
    }

    @Test
    @DisplayName("resetToDefaults conserva códigos ajenos a la lista de defaults")
    void resetToDefaults_preservesForeignCodes() {
        // COMAT / RED_TAG / LIVE_PLANTS vienen del seed V23 y NO están en los 25
        // defaults del service: deben sobrevivir al "restore".
        final List<String> foreign = List.of("COMAT", "RED_TAG", "LIVE_PLANTS");
        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());

        service.resetToDefaults();

        ArgumentCaptor<CommodityTypeEntity> captor = ArgumentCaptor.forClass(CommodityTypeEntity.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        List<String> saved = captor.getAllValues().stream().map(CommodityTypeEntity::getCode).toList();
        for (String code : foreign) {
            assertFalse(saved.contains(code),
                "resetToDefaults no debe reinsertar ni tocar " + code + " (debe quedar intacto en BD)");
        }
    }

    @Test
    @DisplayName("resetToDefaults inserta los defaults ausentes y cuenta solo los insertados")
    void resetToDefaults_insertsMissingAndReturnsCount() {
        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());

        int restored = service.resetToDefaults();

        assertEquals(25, restored, "Con el catálogo vacío deben insertarse los 25 defaults");
    }

    @Test
    @DisplayName("resetToDefaults refresca label/description/color y reactiva los existentes")
    void resetToDefaults_refreshesExistingRows() {
        // DRY_CARGO existe pero con label y description editadas por el admin y
        // desactivada: el restore debe devolverla a los valores canónicos.
        // OJO: el stub genérico va PRIMERO — en Mockito gana el último stub que
        // coincide, así que el específico debe declararse después.
        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.findByCodeIgnoreCase("DRY_CARGO"))
            .thenReturn(Optional.of(row("DRY_CARGO", "Editado por admin", "Descripción custom")));

        int restored = service.resetToDefaults();

        ArgumentCaptor<CommodityTypeEntity> captor = ArgumentCaptor.forClass(CommodityTypeEntity.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        CommodityTypeEntity dry = captor.getAllValues().stream()
            .filter(e -> "DRY_CARGO".equals(e.getCode())).findFirst().orElseThrow();
        assertEquals("Dry Cargo", dry.getLabel());
        assertEquals("General dry cargo", dry.getDescription());
        assertTrue(dry.getIsActive(), "Un default debe quedar siempre activo");
        assertEquals(24, restored, "Solo los 24 defaults realmente ausentes se cuentan como restaurados");
    }

    @Test
    @DisplayName("Los defaults cubren los códigos legacy que la UI usa en specialItems")
    void defaults_coverLegacySpecialItemCodes() {
        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());

        service.resetToDefaults();

        ArgumentCaptor<CommodityTypeEntity> captor = ArgumentCaptor.forClass(CommodityTypeEntity.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        List<String> saved = captor.getAllValues().stream().map(CommodityTypeEntity::getCode).toList();
        // Los 7 códigos que UldsView resuelve vía descriptionOf()/labelOf().
        for (String code : List.of("SDQ_SDF", "SDQ_MIA", "WWEF", "FCC", "EMPTY_ULD", "EMPTY_BAGS", "NETS")) {
            assertTrue(saved.contains(code), "El catálogo debe incluir el código legacy " + code);
        }
    }

    @Test
    @DisplayName("Todos los defaults llevan description (es el texto del tooltip en la UI)")
    void defaults_allHaveDescription() {
        when(repository.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());

        service.resetToDefaults();

        ArgumentCaptor<CommodityTypeEntity> captor = ArgumentCaptor.forClass(CommodityTypeEntity.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        captor.getAllValues().forEach(e -> {
            assertTrue(e.getDescription() != null && !e.getDescription().isBlank(),
                "El default " + e.getCode() + " necesita description para el tooltip de la UI");
            assertTrue(e.getColor() != null && !e.getColor().isBlank(),
                "El default " + e.getCode() + " necesita color");
        });
    }
}
