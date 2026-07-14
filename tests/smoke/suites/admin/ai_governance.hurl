# tags: admin,ai-governance,security

GET {{base_url}}/api/auth/me
Accept: application/json
HTTP 401
[Captures]
csrf_token: cookie "XSRF-TOKEN"
[Asserts]
jsonpath "$.success" == false
jsonpath "$.error.code" == "AUTH_UNAUTHENTICATED"

POST {{base_url}}/api/auth/register
Content-Type: application/json
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
{
  "email": "governance-{{smoke_email}}",
  "password": "{{smoke_password}}",
  "displayName": "{{smoke_display_name}} Governance"
}
HTTP 200
[Captures]
user_id: jsonpath "$.data.id"
[Asserts]
jsonpath "$.success" == true

GET {{base_url}}/api/admin/ai/settings
Accept: application/json
HTTP 403

GET {{base_url}}/api/admin/ai/model-prices
Accept: application/json
HTTP 403

GET {{base_url}}/api/admin/ai/usage/summary
Accept: application/json
HTTP 403

GET {{base_url}}/api/admin/ai/usage/by-user
Accept: application/json
HTTP 403

GET {{base_url}}/api/admin/ai/usage/by-model
Accept: application/json
HTTP 403

GET {{base_url}}/api/admin/ai/usage/by-source
Accept: application/json
HTTP 403

GET {{base_url}}/api/admin/users/{{user_id}}/ai-policy
Accept: application/json
HTTP 403

PATCH {{base_url}}/api/admin/ai/settings
Content-Type: application/json
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
{
  "aiEnabled": true,
  "defaultDailyRequestLimit": 50
}
HTTP 403

PATCH {{base_url}}/api/admin/users/{{user_id}}/ai-policy
Content-Type: application/json
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
{
  "aiEnabledOverride": false,
  "dailyRequestLimitOverride": 50
}
HTTP 403

POST {{base_url}}/api/admin/ai/model-prices
Content-Type: application/json
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
{
  "provider": "smoke",
  "model": "governance-{{run_id}}",
  "inputPricePerMillion": "1.00000000",
  "cachedInputPricePerMillion": "0.10000000",
  "outputPricePerMillion": "2.00000000",
  "costMultiplier": "1.000000",
  "enabled": true
}
HTTP 403

PATCH {{base_url}}/api/admin/ai/model-prices/1
Content-Type: application/json
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
{
  "provider": "smoke",
  "model": "governance-{{run_id}}",
  "inputPricePerMillion": "1.00000000",
  "cachedInputPricePerMillion": "0.10000000",
  "outputPricePerMillion": "2.00000000",
  "costMultiplier": "1.000000",
  "enabled": false
}
HTTP 403
