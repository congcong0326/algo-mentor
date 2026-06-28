# tags: critical,practice,learning-plan

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
  "email": "practice-{{smoke_email}}",
  "password": "{{smoke_password}}",
  "displayName": "{{smoke_display_name}} Practice"
}
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.email" == "practice-{{smoke_email}}"
jsonpath "$.data.status" == "ACTIVE"

POST {{base_url}}/api/learning-plans/drafts
Content-Type: application/json
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
{
  "intent": "INTERVIEW_SPRINT",
  "goal": "{{smoke_goal}} practice session",
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
jsonpath "$.data.draftPlan.phases[0].phaseIndex" exists
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
[Captures]
phase_index: jsonpath "$.data.phases[0].phaseIndex"
problem_slug: jsonpath "$.data.phases[0].problems[0].slug"
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.id" == {{plan_id}}
jsonpath "$.data.phases[0].problems[0].slug" exists

POST {{base_url}}/api/learning-plans/{{plan_id}}/phases/{{phase_index}}/problems/{{problem_slug}}/practice-session?locale=zh-CN
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
HTTP 200
[Captures]
session_id: jsonpath "$.data.session.id"
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.session.planId" == {{plan_id}}
jsonpath "$.data.session.phaseIndex" == {{phase_index}}
jsonpath "$.data.session.problemSlug" == "{{problem_slug}}"
jsonpath "$.data.session.progressStatus" == "IN_PROGRESS"
jsonpath "$.data.problem.slug" == "{{problem_slug}}"
jsonpath "$.data.messages[0].messageType" == "PROBLEM_STATEMENT"
jsonpath "$.data.completionGate.canComplete" == false

GET {{base_url}}/api/practice-sessions/{{session_id}}
Accept: application/json
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.session.id" == {{session_id}}
jsonpath "$.data.session.planId" == {{plan_id}}
jsonpath "$.data.problem.slug" == "{{problem_slug}}"

GET {{base_url}}/api/practice-sessions/{{session_id}}/messages?limit=10
Accept: application/json
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data[0].messageType" == "PROBLEM_STATEMENT"

GET {{base_url}}/api/practice-sessions/{{session_id}}/reviews
Accept: application/json
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.data.reviews" exists
jsonpath "$.data.completionGate.canComplete" == false
jsonpath "$.data.completionGate.reasonCode" == "NO_REVIEW"

PATCH {{base_url}}/api/practice-sessions/{{session_id}}/progress-status
Content-Type: application/json
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
{
  "status": "NOT_STARTED"
}
HTTP 400
[Asserts]
jsonpath "$.success" == false
jsonpath "$.error.code" == "PRACTICE_PROGRESS_STATUS_UNSUPPORTED"

DELETE {{base_url}}/api/learning-plans/{{plan_id}}
Accept: application/json
X-XSRF-TOKEN: {{csrf_token}}
HTTP 200
[Asserts]
jsonpath "$.success" == true
