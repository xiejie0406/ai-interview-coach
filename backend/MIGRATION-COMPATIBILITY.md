# Legacy backend migration compatibility source

`backend/` is retained as a read-only migration reference while AI Interview Coach converges into
`apps/platform-backend/ruoyi-interview`. It is not a formal runtime entry.

- The formal service is `apps/platform-backend/ruoyi-admin` and the only main class is `com.ruoyi.RuoYiApplication`.
- `InterviewCoachApplication` refuses to start unless `AI_INTERVIEW_LEGACY_COMPATIBILITY=true` is explicitly set.
- The old `SecurityConfiguration` is disabled unless `legacy.ai-backend.enabled=true`; it must never be enabled alongside RuoYi.
- Old identity controllers, `AIC_SESSION`, `AIC-XSRF-TOKEN`, PostgreSQL identity tables, and the old WebSocket/SSE chain are deletion candidates only after data, interface, rollback, and user acceptance gates pass.
- Do not delete or move this directory without a separate authorization decision.
