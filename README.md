# Dispatch Load Balancer

Spring Boot REST service that allocates delivery orders to a fleet of vehicles. It respects vehicle
capacity, serves higher-priority orders first, and keeps total travel distance low using the
Haversine formula. No UI: test it with Swagger UI or Postman.

| | |
|---|---|
| Live URL |https://dispatch-load-balancer-b8wa.onrender.com|
| Swagger UI | https://dispatch-load-balancer-b8wa.onrender.com/swagger-ui/index.html|
| Loom walkthrough | _add Loom link here_ |

## Tech stack

Java 21, Spring Boot 3.3, Spring Data JPA, Bean Validation, springdoc-openapi (Swagger),
PostgreSQL, JUnit 5, AssertJ, Mockito.

## Run locally

Requirements: JDK 21+ and Maven 3.9+.

```bash
mvn spring-boot:run
```

* API: http://localhost:8080
* Swagger UI: http://localhost:8080/swagger-ui.html

### Use PostgreSQL

```bash
export DB_URL=jdbc:postgresql://HOST:5432/DBNAME
export DB_USER=...
export DB_PASSWORD=...
mvn spring-boot:run
```

Tables are created automatically (`spring.jpa.hibernate.ddl-auto=update`).

### Run with Docker

```bash
docker build -t dispatch-load-balancer .
docker run -p 8080:8080 dispatch-load-balancer
# with Postgres: add -e DB_URL=... -e DB_USER=... -e DB_PASSWORD=...
```

The app listens on `$PORT` when set (Render, Railway, Fly.io), otherwise 8080.

## Run the tests

```bash
mvn test
```

Coverage report: `target/site/jacoco/index.html`.

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/dispatch/orders` | Submit delivery orders (upsert by `orderId`) |
| POST | `/api/dispatch/vehicles` | Submit fleet details (upsert by `vehicleId`) |
| GET | `/api/dispatch/plan` | Compute and return the dispatch plan |

Full schemas, validation rules and example payloads are in Swagger UI.

### Quick start with curl

Sample data (30 orders, 5 vehicles from the assignment) is in `samples/`.

```bash
curl -X POST localhost:8080/api/dispatch/orders   -H 'Content-Type: application/json' -d @samples/sample-orders.json
curl -X POST localhost:8080/api/dispatch/vehicles -H 'Content-Type: application/json' -d @samples/sample-vehicles.json
curl localhost:8080/api/dispatch/plan
```

### Postman

Import `postman/dispatch-load-balancer.postman_collection.json`. It contains the three happy-path
requests (with the full sample data) plus three error-case requests. Set the `baseUrl` collection
variable to your deployed URL when testing the live service.

### Plan response

```json
{
  "dispatchPlan": [
    {
      "vehicleId": "VEH001",
      "currentAddress": "Karol Bagh, Delhi, India",
      "capacity": 100.0,
      "totalLoad": 100.0,
      "totalDistance": "14.44 km",
      "totalDistanceKm": 14.44,
      "assignedOrders": [ { "orderId": "ORD026", "latitude": 28.7041, "longitude": 77.1025,
                            "address": "Karol Bagh, Delhi, India", "packageWeight": 25.0, "priority": "HIGH" } ]
    }
  ],
  "unassignedOrders": [
    { "order": { "orderId": "ORD016", "...": "..." }, "reason": "No vehicle has enough remaining capacity" }
  ],
  "summary": { "totalOrders": 30, "assignedOrders": 22, "unassignedOrders": 8,
               "totalVehicles": 5, "totalLoad": 490.0, "totalDistanceKm": 73.42 }
}
```

`unassignedOrders` and `summary` are additions to the sample in the brief, so unassignable orders are
reported explicitly rather than silently dropped.

With the supplied sample, total demand is 645 but the fleet can carry only 500, so some orders cannot
be assigned by definition. The plan places every HIGH and MEDIUM order and leaves 8 LOW orders unassigned.

### Error responses

All errors share one shape:

```json
{ "status": "error", "message": "Validation failed",
  "errors": ["orders[0].latitude: latitude must be between -90 and 90"], "timestamp": "..." }
```

| Situation | HTTP |
|---|---|
| Missing/blank field, latitude/longitude out of range, weight or capacity <= 0, empty list | 400 |
| Unknown `priority` (e.g. `URGENT`), wrong JSON type, malformed JSON, missing body | 400 |
| Duplicate `orderId` / `vehicleId` inside one request | 400 |
| Non-JSON `Content-Type` | 415 |
| Unsupported HTTP method | 405 |
| `GET /plan` before orders and/or vehicles were submitted | 409 |
| Database unavailable | 503 |
| Anything unexpected (details are logged, not returned) | 500 |

Orders that are too heavy for every vehicle, or that no longer fit anywhere, are not errors: they are
returned in `unassignedOrders` with a reason.

## How the allocation works

1. **Order sequence**: HIGH, then MEDIUM, then LOW. Within a priority, heavier packages first (better
   packing), then by `orderId` so output is deterministic.
2. **Vehicle choice**: for each order, among vehicles with enough remaining capacity, pick the one whose
   last stop (initially its current position) is nearest by Haversine distance. Ties go to the vehicle
   with less remaining capacity (best fit).
3. **Unassigned**: an order that fits no vehicle is reported with a reason.
4. **Route refinement**: each vehicle's stops are re-ordered with 2-opt to shorten its path (skipped for
   routes over 150 stops to bound the cost).

Complexity of the allocation phase is O(n log n + n·m) for n orders and m vehicles.

### Assumptions and limits

* Distance is the great-circle (Haversine) distance in km, Earth radius 6371.0088 km.
* A vehicle's distance runs from its current position through its stops in order. There is **no return
  trip**.
* Weight and capacity are in the same unit and may be decimals.
* The plan is computed on demand from everything stored, so re-submitting orders or vehicles changes the
  next plan.
* This is a greedy heuristic plus local search. It always satisfies capacity and priority rules, but it
  does not guarantee the globally minimal distance (that is the NP-hard vehicle routing problem).
* Priority is strict: a HIGH order is always considered before any MEDIUM or LOW order, even if serving
  it costs extra distance.

## Design

```
controller/   REST endpoints, thin (validation via @Valid)
service/      DispatchService (upsert + orchestration), PlanMapper (domain -> response)
allocation/   AllocationStrategy (Strategy pattern) -> GreedyNearestVehicleStrategy,
              RouteOptimizer (2-opt), VehicleRoute (working state)
distance/     DistanceCalculator interface -> HaversineDistanceCalculator
model/        JPA entities (OrderEntity, VehicleEntity), Priority enum
repository/   Spring Data JPA repositories
dto/          Request/response records
exception/    GlobalExceptionHandler + custom exceptions
```

* **Strategy pattern**: `AllocationStrategy` and `DistanceCalculator` can be swapped without touching the
  service (e.g. road distance, a different heuristic).
* **Efficient upsert**: ids are the primary keys. A request's existing rows are loaded with chunked bulk
  `IN` queries (not one SELECT per row), updated or created in memory, then written with one `saveAll`
  with JDBC batching enabled. Entities implement `Persistable` so new rows are INSERTed directly.
* **Performance**: the allocation phase is a single pass over sorted orders; 5,000 orders across 200
  vehicles allocates in well under a second.

## Tests

* `HaversineDistanceCalculatorTest`: known distances, symmetry, antipodal edge case.
* `GreedyNearestVehicleStrategyTest`: priority order, nearest vehicle, capacity never exceeded
  (randomised), oversized and unassignable orders, no vehicles, ties, determinism, large dataset.
* `RouteOptimizerTest`: 2-opt reorders correctly and never lengthens a route.
* `DispatchControllerTest` (`@WebMvcTest`): every validation and error path.
* `DispatchIntegrationTest` (`@SpringBootTest` + H2): the full 30-order sample end to end, upsert
  behaviour, duplicate ids, 409 cases, Swagger docs published.

## Deploying (example: Render + Neon)

1. Create a free PostgreSQL database on Neon and copy its JDBC URL, user and password.
2. Push this project to GitHub. On Render create a **Web Service** from the repo, runtime **Docker**.
3. Set environment variables `DB_URL` (`jdbc:postgresql://...?sslmode=require`), `DB_USER`, `DB_PASSWORD`.
4. Deploy, then open `<url>/swagger-ui.html`. Free tiers sleep when idle, so the first request can take
   up to a minute.
