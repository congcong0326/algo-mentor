# tags: admin,ai-governance,requires-admin

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
  "email": "{{smoke_admin_email}}",
  "password": "{{smoke_admin_password}}",
  "displayName": "{{smoke_admin_display_name}}"
}
HTTP 200
[Captures]
admin_user_id: jsonpath "$.data.id"
[Asserts]
jsonpath "$.success" == true

GET {{base_url}}/api/admin/ai/settings
Accept: application/json
HTTP 200
[Captures]
settings_enabled: jsonpath "$.data.aiEnabled"
settings_limit: jsonpath "$.data.defaultDailyRequestLimit"
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.defaultDailyRequestLimit" exists

PATCH {{base_url}}/api/admin/ai/settings
Content-Type: application/json
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
{
  "aiEnabled": {{settings_enabled}},
  "defaultDailyRequestLimit": {{settings_limit}}
}
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.defaultDailyRequestLimit" == {{settings_limit}}

PATCH {{base_url}}/api/admin/users/{{admin_user_id}}/ai-policy
Content-Type: application/json
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
{
  "aiEnabledOverride": false,
  "dailyRequestLimitOverride": 31
}
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.aiEnabledOverride" == false
jsonpath "$.data.dailyRequestLimitOverride" == 31

PATCH {{base_url}}/api/admin/users/{{admin_user_id}}/ai-policy
Content-Type: application/json
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
{
  "aiEnabledOverride": null,
  "dailyRequestLimitOverride": null
}
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.aiEnabledOverride" == null
jsonpath "$.data.dailyRequestLimitOverride" == null

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
HTTP 200
[Captures]
price_id: jsonpath "$.data.id"
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.provider" == "smoke"
jsonpath "$.data.model" == "governance-{{run_id}}"
jsonpath "$.data.enabled" == true

PATCH {{base_url}}/api/admin/ai/model-prices/{{price_id}}
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
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.enabled" == false

GET {{base_url}}/api/admin/ai/model-prices
Accept: application/json
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.items" exists
jsonpath "$.data.unpricedModels" exists

GET {{base_url}}/api/admin/ai/usage/summary
Accept: application/json
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.admittedEntryRequestCount" exists
jsonpath "$.data.estimatedCostUsd" exists

GET {{base_url}}/api/admin/ai/usage/by-user?page=1&pageSize=20
Accept: application/json
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.items" exists

GET {{base_url}}/api/admin/ai/usage/by-model
Accept: application/json
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data" exists

GET {{base_url}}/api/admin/ai/usage/by-source
Accept: application/json
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data" exists
