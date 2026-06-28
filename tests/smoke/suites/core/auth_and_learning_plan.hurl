# tags: critical,auth,learning-plan

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
  "email": "{{smoke_email}}",
  "password": "{{smoke_password}}",
  "displayName": "{{smoke_display_name}}"
}
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.email" == "{{smoke_email}}"
jsonpath "$.data.displayName" == "{{smoke_display_name}}"
jsonpath "$.data.status" == "ACTIVE"
jsonpath "$.data.roles[0]" == "USER"

GET {{base_url}}/api/auth/me
Accept: application/json
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.email" == "{{smoke_email}}"

POST {{base_url}}/api/learning-plans/drafts
Content-Type: application/json
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
{
  "intent": "INTERVIEW_SPRINT",
  "goal": "{{smoke_goal}}",
  "durationWeeks": 4,
  "level": "INTERMEDIATE",
  "weeklyHours": 6,
  "programmingLanguage": "Java",
  "difficultyPreference": "MEDIUM",
  "interviewOriented": true,
  "topicPreferences": ["Array", "Hash Table"]
}
HTTP 200
[Captures]
draft_id: jsonpath "$.data.draftId"
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.status" == "GENERATED"
jsonpath "$.data.draftPlan.goal" == "{{smoke_goal}}"
jsonpath "$.data.draftPlan.programmingLanguage" == "Java"
jsonpath "$.data.draftPlan.phases[0].problems[0].slug" exists

POST {{base_url}}/api/learning-plans/drafts/{{draft_id}}/confirm
Content-Type: application/json
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
HTTP 200
[Captures]
plan_id: jsonpath "$.data.planId"
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.status" == "ACTIVE"

GET {{base_url}}/api/learning-plans/{{plan_id}}
Accept: application/json
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.id" == {{plan_id}}
jsonpath "$.data.goal" == "{{smoke_goal}}"
jsonpath "$.data.status" == "ACTIVE"
jsonpath "$.data.programmingLanguage" == "Java"

DELETE {{base_url}}/api/learning-plans/{{plan_id}}
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
HTTP 200
[Asserts]
jsonpath "$.success" == true
