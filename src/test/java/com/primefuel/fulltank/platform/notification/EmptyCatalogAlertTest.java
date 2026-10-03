package com.primefuel.fulltank.platform.notification;

import com.primefuel.fulltank.platform.iam.api.LegacyCompanyDirectory;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.application.queryservices.FuelProductQueryService;
import com.primefuel.fulltank.platform.inventory.domain.model.aggregates.FuelProduct;
import com.primefuel.fulltank.platform.inventory.domain.model.queries.GetFuelProductsByProviderIdQuery;
import com.primefuel.fulltank.platform.inventory.interfaces.rest.FuelProductsController;
import com.primefuel.fulltank.platform.shared.events.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmptyCatalogAlertTest {
    @Test
    void resolvesOnlyAnActiveDistributorWithAMatchingProviderMember() {
        var organizations = mock(com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationRepository.class);
        var users = mock(com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository.class);
        var memberships = mock(com.primefuel.fulltank.platform.iam.domain.repositories.MembershipRepository.class);
        var providers = mock(com.primefuel.fulltank.platform.iam.domain.repositories.ProviderCompanyRepository.class);
        var directory = new com.primefuel.fulltank.platform.iam.infrastructure.services.LegacyCompanyDirectoryImpl(
                mock(com.primefuel.fulltank.platform.iam.domain.repositories.BuyerCompanyRepository.class),
                organizations, users, memberships, providers);
        var provider = new com.primefuel.fulltank.platform.iam.domain.model.aggregates.ProviderCompany();
        provider.setRuc("123");
        var organization = new com.primefuel.fulltank.platform.iam.domain.model.aggregates.Organization();
        organization.setId(40L);
        organization.setType(com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType.DISTRIBUTOR);
        var member = new com.primefuel.fulltank.platform.iam.domain.model.aggregates.Membership();
        member.setUserId(10L);
        var user = new com.primefuel.fulltank.platform.iam.domain.model.aggregates.User();
        user.setProviderId(2L);
        when(providers.findById(2L)).thenReturn(Optional.of(provider));
        when(organizations.findByRuc("123")).thenReturn(Optional.of(organization));
        when(memberships.findActiveByOrganizationId(40L)).thenReturn(List.of(member));
        when(users.findById(10L)).thenReturn(Optional.of(user));
        assertThat(directory.organizationIdForProvider(2L)).contains(40L);
        user.setProviderId(3L);
        assertThat(directory.organizationIdForProvider(2L)).isEmpty();
        user.setProviderId(2L);
        organization.deactivate();
        assertThat(directory.organizationIdForProvider(2L)).isEmpty();
        assertThat(directory.organizationIdForProvider(null)).isEmpty();
    }

    @Test
    void verifiesCatalogAndTargetsTheProviderOrganizationWithADailyKey() {
        var query = mock(FuelProductQueryService.class);
        var companies = mock(LegacyCompanyDirectory.class);
        var events = mock(ApplicationEventPublisher.class);
        var clock = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC);
        var controller = new FuelProductsController(mock(FuelProductCommandService.class), query,
                mock(TenantAccess.class), companies, events, clock);
        when(query.handle(new GetFuelProductsByProviderIdQuery(2L))).thenReturn(List.of());
        when(companies.organizationIdForProvider(2L)).thenReturn(Optional.of(40L));
        assertThat(controller.alertEmptyCatalog(2L).getStatusCode().value()).isEqualTo(204);
        assertThat(controller.alertEmptyCatalog(2L).getStatusCode().value()).isEqualTo(204);
        var envelopes = org.mockito.ArgumentCaptor.forClass(EventEnvelope.class);
        verify(events, times(2)).publishEvent(envelopes.capture());
        assertThat(envelopes.getValue().organizationId()).isEqualTo(40L);
        assertThat(envelopes.getAllValues().get(0).eventId()).isEqualTo(envelopes.getValue().eventId());

        clearInvocations(events);
        var product = mock(FuelProduct.class);
        when(product.getActive()).thenReturn(true);
        when(query.handle(new GetFuelProductsByProviderIdQuery(2L))).thenReturn(List.of(product));
        assertThat(controller.alertEmptyCatalog(2L).getStatusCode().value()).isEqualTo(409);
        verifyNoInteractions(events);
        when(product.getActive()).thenReturn(false);
        assertThat(controller.alertEmptyCatalog(2L).getStatusCode().value()).isEqualTo(204);
        clearInvocations(events);
        when(companies.organizationIdForProvider(2L)).thenReturn(Optional.empty());
        assertThat(controller.alertEmptyCatalog(2L).getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(events);
    }
}
