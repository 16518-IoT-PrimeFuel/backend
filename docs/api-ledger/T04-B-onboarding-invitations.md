# T04-B — Onboarding, invitations and IAM compatibility (S04)

Parent spec: S04. Precondition: T04-A. **Closes S04.**

## Signup v1 — dual-write (no more orphan companies)

`UserCommandServiceImpl.handle(SignUpCommand)` now creates, in the same transaction as the legacy
company + user:

- an `Organization` (`CUSTOMER` for a buyer, `DISTRIBUTOR` for a provider) keyed by the company RUC;
- an `OWNER` `Membership` linking that organization to the new user.

If the organization cannot be created the signup fails; if the membership cannot be granted the
transaction rolls back. The legacy `BuyerCompany`/`ProviderCompany` and `User.companyId`/`providerId`
are still written — nothing legacy was dropped.

The public `POST /api/v1/buyer-companies` and `/api/v1/provider-companies` remain for backwards
compatibility (removing them would change the frozen 77-operation v1 ledger); they are superseded by
v2 onboarding and are to be retired in the contract wave (T22).

## Login v1 — dual-read

`AuthenticatedUserResource` keeps `id`, `username`, `token`, `roles`, `companyId`, `providerId` and
adds `memberships: [{organizationId, role}]` resolved from the user's active memberships.

## v2 endpoints (additive, authenticated)

- `POST /api/v2/onboarding` — creates an organization and makes the **authenticated principal** its
  `OWNER` (owner comes from the token, never the body).
- `POST /api/v2/organizations/{organizationId}/invitations` — `@PreAuthorize` owner check via
  `@membershipAccess.belongsToOrganization`.
- `POST /api/v2/invitations/{token}/accept` — accepts as the authenticated principal.
- `DELETE /api/v2/invitations/{invitationId}` — revoke (owner-scoped).

## Invitation rules

Token = UUID, TTL 7 days, `status` ∈ `PENDING|ACCEPTED|REVOKED`, unique token. Inviting an email with
an existing `PENDING` invitation is a conflict. Accepting requires the invitation to be *usable*
(`PENDING` and not expired) and grants the membership; a token that was accepted, revoked or expired
is rejected, and revocation is effective on the next request.

## Persistence / validation

`V6__organization_invitations.sql` (`organization_invitations`, unique `token`). Validated with
Hibernate `validate` on local MySQL 8.0.46; V1→V6 apply cleanly.

## Tests

- `InvitationFlowTest`: invite → duplicate conflict → accept creates membership → token reuse fails →
  expired invitation fails → revoke → revoked token fails and grants no membership.
- `SignUpOnboardingTest`: buyer signup produces a `CUSTOMER` organization + active `OWNER` membership.

## Deferred (not in this ticket)

Retiring the public company-creation endpoints (blocked by the frozen v1 ledger); dropping legacy
roles/`companyId`/`providerId`; backfilling pre-existing companies/users into organizations and
memberships (T05-B); invitation e-mail delivery.

Build: 41/41 green.
