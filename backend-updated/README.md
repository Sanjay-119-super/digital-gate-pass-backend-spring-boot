# Digital Gate Pass — Backend (Spring Boot 3.3, Java 21)

Production-ready REST backend powering the Digital Gate Pass System.

## Stack
- Spring Boot 3.3 · Java 21
- Spring Security + JWT (access + rotating refresh tokens)
- Spring Data JPA + MySQL 8 + Flyway migrations
- Redis caching for QR verification lookups
- Async event listeners for notifications
- Optimistic locking (`@Version`) on passes — prevents race conditions on concurrent warden approvals (returns `409`)
- OpenAPI/Swagger (`/swagger-ui.html`)
- Actuator + Prometheus (`/actuator/prometheus`)

## Run locally
```bash
docker compose up --build
# API:    http://localhost:8080
# Swagger http://localhost:8080/swagger-ui.html
```

## Key endpoints
| Method | Path | Role |
|---|---|---|
| POST | `/api/auth/register` | public |
| POST | `/api/auth/login` | public |
| POST | `/api/auth/refresh` | public |
| POST | `/api/passes` | STUDENT |
| GET  | `/api/passes/me` | STUDENT |
| POST | `/api/passes/{id}/cancel` | STUDENT |
| GET  | `/api/passes/pending` | WARDEN |
| POST | `/api/passes/{id}/approve` | WARDEN |
| POST | `/api/passes/{id}/reject`  | WARDEN |
| GET  | `/api/passes/{id}/history` | any auth |
| GET  | `/api/verify/{qrToken}` | SECURITY |
| POST | `/api/verify/scan` | SECURITY |

## Environment
| Var | Default |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/gatepass...` |
| `DB_USER` / `DB_PASS` | `root` / `root` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `JWT_SECRET` | **change in prod** |

## Tests
```bash
mvn test
```

## Kubernetes
`k8s/deployment.yaml` ships a Deployment, Service, and HPA (3–20 replicas, 70% CPU).
