# Engineering Notes & Decisions

## Architecture Decisions
- **Layered Architecture:** Controller -> Service -> Repository. No business logic in controllers.
- **DTOs:** Never expose JPA entities directly through API endpoints.
- **Concurrency & Stock Integrity:** Optimistic locking via JPA `@Version` on `Product`.
- **Idempotency:** Unique idempotency key per customer order submission.

