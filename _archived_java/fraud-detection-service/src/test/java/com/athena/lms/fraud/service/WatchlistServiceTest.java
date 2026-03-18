package com.athena.lms.fraud.service;

import com.athena.lms.fraud.dto.request.CreateWatchlistEntryRequest;
import com.athena.lms.fraud.dto.response.WatchlistEntryResponse;
import com.athena.lms.fraud.entity.WatchlistEntry;
import com.athena.lms.fraud.enums.WatchlistType;
import com.athena.lms.fraud.repository.WatchlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock private WatchlistRepository watchlistRepository;
    @Mock private CaseManagementService caseManagementService;

    @InjectMocks private WatchlistService service;

    private static final String TENANT = "test-tenant";

    @Nested
    @DisplayName("Watchlist Entry Creation")
    class CreationTests {

        @Test
        @DisplayName("creates watchlist entry")
        void createEntry() {
            when(watchlistRepository.save(any())).thenAnswer(inv -> {
                WatchlistEntry e = inv.getArgument(0);
                e.setId(UUID.randomUUID());
                return e;
            });

            CreateWatchlistEntryRequest req = new CreateWatchlistEntryRequest();
            req.setListType("PEP");
            req.setEntryType("INDIVIDUAL");
            req.setName("Jane Smith");
            req.setNationalId("ID-99999");
            req.setPhone("+254700000000");
            req.setReason("Politically exposed person");
            req.setSource("Government gazette");

            WatchlistEntryResponse result = service.createEntry(req, TENANT);

            assertThat(result.getListType()).isEqualTo(WatchlistType.PEP);
            assertThat(result.getEntryType()).isEqualTo("INDIVIDUAL");
            assertThat(result.getName()).isEqualTo("Jane Smith");
            assertThat(result.getNationalId()).isEqualTo("ID-99999");
            assertThat(result.getActive()).isTrue();

            verify(watchlistRepository).save(any());
            verify(caseManagementService).audit(eq(TENANT), eq("WATCHLIST_ENTRY_CREATED"),
                    eq("WATCHLIST"), any(), eq("system"), contains("Jane Smith"), isNull());
        }
    }

    @Nested
    @DisplayName("Watchlist Entry Listing")
    class ListTests {

        @Test
        @DisplayName("lists active entries")
        void listActiveEntries() {
            WatchlistEntry entry = WatchlistEntry.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT)
                    .listType(WatchlistType.SANCTIONS).entryType("ENTITY")
                    .name("Bad Corp").active(true)
                    .build();

            Pageable pageable = PageRequest.of(0, 20);
            Page<WatchlistEntry> page = new PageImpl<>(List.of(entry), pageable, 1);
            when(watchlistRepository.findByTenantIdAndActive(TENANT, true, pageable)).thenReturn(page);

            var result = service.listEntries(TENANT, true, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Bad Corp");
            assertThat(result.getContent().get(0).getActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Watchlist Entry Deactivation")
    class DeactivationTests {

        @Test
        @DisplayName("deactivates entry")
        void deactivateEntry() {
            UUID entryId = UUID.randomUUID();
            WatchlistEntry entry = WatchlistEntry.builder()
                    .id(entryId).tenantId(TENANT)
                    .listType(WatchlistType.INTERNAL_BLACKLIST).entryType("INDIVIDUAL")
                    .name("Suspect Person").active(true)
                    .build();

            when(watchlistRepository.findById(entryId)).thenReturn(Optional.of(entry));
            when(watchlistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WatchlistEntryResponse result = service.deactivateEntry(entryId, TENANT);

            assertThat(result.getActive()).isFalse();
            assertThat(result.getName()).isEqualTo("Suspect Person");

            verify(caseManagementService).audit(eq(TENANT), eq("WATCHLIST_ENTRY_DEACTIVATED"),
                    eq("WATCHLIST"), eq(entryId), eq("system"), contains("Suspect Person"), isNull());
        }
    }
}
