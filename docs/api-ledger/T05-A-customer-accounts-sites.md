# T05-A — CustomerAccount y sitios (S05)

Parent spec: S05. Precondition: T04-B.

## What was added (module `equipment`)

- Aggregates `CustomerAccount` (`organizationId`, `name`, `ruc`, `address`, `contactEmail`, `phone`,
  `legacyCompanyId`, `active`) and `CustomerSite` (`organizationId`, `customerAccountId`, `name`,
  `address`, `active`); commands `RegisterCustomerCommand` / `RegisterSiteCommand`; queries
  `GetCustomerByIdQuery` / `GetCustomersByOrganizationQuery` / `GetSitesByCustomerQuery`.
- `CustomerCommandService` / `CustomerQueryService` with `Result`/`ApplicationError`. Duplicate RUC in
  the same organization is a conflict; a site whose organization differs from the customer's is
  forbidden. A site belongs to the same organization as its customer (invariant from S05).
- Persistence (`V7__customer_accounts_sites.sql`, validated with Hibernate `validate` on MySQL
  8.0.46): `customer_accounts` (unique `legacy_company_id` = the `companyId → customerId` map),
  `customer_sites`.
- Public seam `equipment.api.CustomerDirectory` (`ownsCustomer`, `customerIdForLegacyCompany`).
- v2 REST: `POST/GET /api/v2/customers`, `POST/GET /api/v2/customers/{id}/sites`. The organization
  always comes from the authenticated principal's memberships (`iam.api.MembershipAccess`), never
  from the body.

## Tests

`CustomersV2Test`: register customer → duplicate RUC conflicts → site from another organization is
rejected → site in the same organization succeeds and is queryable.

Build green; ArchUnit baseline unchanged.

## Asunciones abiertas

- **A1 — RUC scope moved from global to per-organization.** Legacy `buyer_companies.ruc` was global;
  in `customer_accounts` uniqueness is per organization. Revisit together (ties to U01/U15).
- **A2 — `legacyCompanyId` is the authoritative `companyId → customerId` map.** No separate mapping
  table was introduced.
- **A3 — Customer creation for legacy rows happens only through the backfill (T05-B)**; no automatic
  customer is created for existing companies here.
