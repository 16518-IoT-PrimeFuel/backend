# T04-A — Organization, Membership and TenantAccess (S04)

Parent spec: S04. Preconditions: T02-B, T03-B. Scope: the **model + resolution** half (T04-B adds
onboarding/invitations REST and the legacy dual-read). Tenant is resolved from the principal, never
from the request body.

## Domain

- Aggregates `Organization` (`name`, `ruc`, `type`, `active`) and `Membership`
  (`organizationId`, `userId`, `role`, `active`), plus VOs `OrganizationType`
  (`DISTRIBUTOR`/`CUSTOMER`) and `MembershipRole` (`OWNER`/`ADMIN`/`MEMBER`).
- Commands `CreateOrganizationCommand`, `GrantMembershipCommand`, `RevokeMembershipCommand`;
  queries `GetOrganizationByIdQuery`, `GetMembershipsByUserIdQuery`.
- Services `OrganizationCommandService`/`MembershipCommandService` (+ query services), returning
  `Result<_, ApplicationError>`. Granting an existing active membership is a conflict; revoking is
  soft (sets `active=false`).

## Persistence

- `organizations` + `memberships` tables via `V5__organizations_memberships.sql` (unique `ruc`;
  unique `(organization_id, user_id)`), entities/assemblers/repository adapters following the existing
  module pattern. `V5` validated under `validate` on local MySQL 8.0.46.

## Public seam (`iam.api`)

- `MembershipAccess`: `currentUserId()`, `currentOrganizationId()`, `belongsToOrganization(Long)`,
  implemented by `MembershipAccessImpl` which reads the authenticated principal's `userId` and
  resolves active memberships. This is the "CurrentAccess resuelve organization/scope sin payload"
  invariant of S02/S04.

## REST

- `GET /api/v2/me/organizations` — organizations the authenticated user belongs to (with their role),
  resolved from `MembershipAccess`, not from any request parameter. Additive; v1 untouched.

## Tests / validation

- `OrganizationMembershipTest`: create organization, grant membership, duplicate-grant conflict,
  revoke deactivates.
- ArchUnit baseline unchanged. Build: 39/39 green.

## Deferred to T04-B

- Onboarding/invitation v2 endpoints and the v1 signup adapter; dual-read of legacy
  roles/`companyId`/`providerId` into memberships; exhaustive actor/scope matrix and
  "revocation applies on the next request"; backfill/mapping. No legacy structures dropped or renamed.
