# Mobile backend contracts

These backend-only contracts replace the inventory/tank and report-download mocks.

## Tank state

Authenticated buyer requests:

```http
GET /api/v2/buyer-companies/{companyId}/sites/{siteId}/tanks
GET /api/v2/buyer-companies/{companyId}/sites/{siteId}/tanks/{tankId}
```

The response is an array or object with `id`, `siteId`, `name`, `fuelType`,
`capacity`, `unit`, `currentLevel`, `status`, and `lastReadingAt`. Values are
the latest validated telemetry state persisted for that tank.

## Report download

Authenticated buyer/provider/admin requests:

```http
GET /api/v2/reports/buyers/{companyId}/export
GET /api/v2/reports/providers/{providerId}/export
GET /api/v2/reports/platform/export
```

Each response is UTF-8 CSV with `Content-Type: text/csv` and an attachment
filename. Buyer/provider exports contain the existing analytics metrics and
monthly totals; the platform export contains the existing global summary.
