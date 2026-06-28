# tags: critical,security,auth

GET {{base_url}}/api/auth/me
Accept: application/json
HTTP 401
[Captures]
csrf_token: cookie "XSRF-TOKEN"
[Asserts]
jsonpath "$.success" == false
jsonpath "$.error.code" == "AUTH_UNAUTHENTICATED"

GET {{base_url}}/api/learning-plans
Accept: application/json
HTTP 401
[Asserts]
jsonpath "$.success" == false
jsonpath "$.error.code" == "AUTH_UNAUTHENTICATED"

GET {{base_url}}/api/abilities/profile
Accept: application/json
HTTP 401
[Asserts]
jsonpath "$.success" == false
jsonpath "$.error.code" == "AUTH_UNAUTHENTICATED"

POST {{base_url}}/api/learning-plans/drafts
Content-Type: application/json
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
{
  "intent": "INTERVIEW_SPRINT",
  "goal": "{{smoke_goal}} anonymous access check",
  "durationWeeks": 4,
  "level": "INTERMEDIATE",
  "weeklyHours": 6,
  "programmingLanguage": "Java",
  "difficultyPreference": "MEDIUM",
  "interviewOriented": true,
  "topicPreferences": ["Array"]
}
HTTP 401
[Asserts]
jsonpath "$.success" == false
jsonpath "$.error.code" == "AUTH_UNAUTHENTICATED"
