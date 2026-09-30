# yarn (backend component)

Yarnspaces chat: MySpace (1-on-1), WeSpace (1-to-many) and Workspace threads, yarns, per-person
archive / pin / mute, yarn requests and blocking. Self-contained: everything lives in this package.
Design and rules: `Please git ignore this/Yarnspaces backend plan.md` (local only).

| File | Role |
|---|---|
| `YarnController` | HTTP shell under `/api/yarns`. No logic. |
| `YarnService` | All the rules (requests, blocks, archive, unread, sorting). One transaction per call. |
| `YarnCaller` | Who is calling. Dev header for now; the **only** file to change when login lands. |
| `YarnDtos` | Request and response records. |
| `YarnErrors` / `YarnException` | Rule violations become `{ "message": ... }` with their own status. Scoped to this controller, so the app-wide handler is untouched. |
| `YarnThread`, `ThreadMember`, `Yarn`, `UserBlock` | Entities (tables `yarn_thread`, `thread_member`, `yarn`, `user_block`). They reference users by id only, with no JPA links into other entities. |
| `Yarn*Repository`, `ThreadMemberRepository`, `UserBlockRepository` | Spring Data repositories. |

## What it needs from the rest of the app

- `User` + `UserRepository` (lookup by username, find by id). Nothing else.
- One line in `SecurityConfig` permitting `/api/yarns/**` (the component authenticates itself through `YarnCaller`).
- `app.dev-identity.enabled` in `application.properties` (env `ALLOW_DEV_IDENTITY`, default off, so production answers 503).

Nothing outside this package refers to it except that security line. To remove chat, delete the package, the line, and the property.

## API (all need `X-Dev-User: <username>` for now)

| Call | Purpose |
|---|---|
| `GET /me`, `GET /directory?q=` | Who am I; find people (hides anyone who blocked me). |
| `GET /threads?view=inbox\|archived&tier=&q=` | My threads, pinned first then newest. |
| `POST /threads/myspace` `{username, body}` | Start a one-to-one yarn request. |
| `POST /threads/group` `{tier, name, usernames[]}` | Create a WeSpace or Workspace. |
| `GET/POST /threads/{id}/yarns` | History (`before`, `limit`) / send. |
| `POST /threads/{id}/read` | Mark read. |
| `PATCH /threads/{id}/prefs` `{archived?, pinned?, muted?}` | Per-person state. |
| `POST /threads/{id}/respond` `{accept}` | Answer a request. |
| `GET /blocks`, `PUT/DELETE /blocks/{userId}` | Blocked list, block, unblock. |

## Editing this component

- Keep rules in `YarnService`; the controller stays a shell.
- Not being a member always answers 404, and a block always answers the same generic 403, so neither leaks who is in a thread or who blocked whom.
- Run the checks with `./mvnw test -Dtest=YarnServiceTest` (in-memory H2, no Postgres needed).
- Tables are created by `ddl-auto=update`, same as the rest of the app. Move to migrations before production data matters.
