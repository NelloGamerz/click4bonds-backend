# Analytics Module

How product analytics travels from a business service to ClickHouse, how to add
an event, and what this pipeline does and does not guarantee.

---

## 1. Architecture

An analytics event is written by whichever service handled the request, pushed
onto Kafka, and stored by a background consumer. No business service talks to
Kafka or ClickHouse.

```
Business Service  (BondService, OrderService, ...)
      |  analyticsService.track(...)
      v
AnalyticsService            builds the event: id, timestamp
      |
      v
AnalyticsEventProducer      KafkaTemplate.send(topic, eventId, event)
      |
      v
Kafka topic: click4bonds.analytics
      |
      v
AnalyticsEventConsumer      @KafkaListener
      |
      v
AnalyticsBatchService       buffers in memory, flush at 1000 or every 5s
      |
      v
ClickHouseService           one JDBC batch insert
      |
      v
click4bonds_analytics.analytics_events
```

Why the indirection: the business request returns as soon as the event reaches
the producer's buffer. Neither Kafka nor ClickHouse is on the request's critical
path, so neither can make a bond page slow — or fail.

### Classes

| Class | Package | Responsibility |
|---|---|---|
| `AnalyticsService` | `Modules.Analytics.Service` | The public API. Builds the event. |
| `AnalyticsEventProducer` | `Modules.Analytics.Producer` | Owns the topic name and record key. |
| `AnalyticsEventConsumer` | `Modules.Analytics.Consumer` | Reads the topic, hands off to the buffer. |
| `AnalyticsBatchService` | `Modules.Analytics.Service` | Holds events, decides when to write. |
| `ClickHouseService` | `Modules.Analytics.Service` | The only class that speaks ClickHouse SQL. |
| `AnalyticsEvent` | `Modules.Analytics.Model` | The event, one field per column. |
| `AnalyticsEventType` | `Modules.Analytics.Model` | The closed set of event types. |
| `KafkaConfig` | `Modules.Analytics.Config` | Topic name, topic creation, partitions. |
| `ClickHouseConfig` | `Modules.Analytics.Config` | The analytics datasource, by name. |

---

## 2. Local infrastructure

Both services run in Docker from `click4bonds-infra/docker-compose.yml`. The
Spring Boot application runs on the host from the IDE and connects to them over
published ports.

| | |
|---|---|
| Kafka | `localhost:9092` |
| Topic | `click4bonds.analytics` |
| ClickHouse HTTP | `localhost:8123` |
| ClickHouse native | `localhost:9000` |
| Database | `click4bonds_analytics` |
| Table | `analytics_events` |
| Consumer group | `click4bonds-analytics` |

Start them:

```bash
cd click4bonds-infra
docker compose up -d
```

### Configuration

`src/main/resources/application-dev.yaml`:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        spring.json.add.type.headers: false

    consumer:
      group-id: click4bonds-analytics
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.click4bonds.app.Modules.Analytics.Model"
        spring.json.value.default.type: "com.click4bonds.app.Modules.Analytics.Model.AnalyticsEvent"
        spring.json.use.type.headers: false

clickhouse:
  url: jdbc:clickhouse://localhost:8123/click4bonds_analytics
  username: default
  password:
```

Two details in there are load-bearing:

- **`spring.json.trusted.packages` and `spring.json.value.default.type` must name
  the real package.** They are the gate that stops a record on the topic from
  choosing the class it deserializes into. A wrong package name here does not
  fail at startup — it fails on the first consumed record.
- **`spring.kafka.*` is only read if `spring-boot-starter-kafka` is on the
  classpath**, not bare `spring-kafka`. Boot 4 keeps Kafka auto-configuration in
  its own module; without the starter there is no `KafkaTemplate` bean at all
  and every property above is silently ignored.

### The ClickHouse user

The compose file sets `CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT: "1"`, and it needs
to keep doing so. Without it the image treats the stock `default` user as
unconfigured and rewrites it to accept connections from `127.0.0.1`/`::1`
*inside the container only*. Every connection arriving through the published
port is then rejected with "Authentication failed", whatever password is used.
That is a local-development-only user with no password — do not copy this
configuration anywhere real.

### The table

```sql
CREATE DATABASE IF NOT EXISTS click4bonds_analytics;

CREATE TABLE click4bonds_analytics.analytics_events
(
    event_id UUID,
    event_type LowCardinality(String),
    user_id Nullable(UInt64),
    session_id String,
    bond_id Nullable(UInt64),
    event_time DateTime64(3),
    source LowCardinality(String),
    page LowCardinality(String),
    metadata String
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(event_time)
ORDER BY (event_type, event_time);
```

Only non-nullable columns are in the sorting key. A `Nullable` column in an
`ORDER BY` makes the primary index unusable for range scans and is rejected
outright in some versions.

`session_id`, `source` and `page` are `String`, not `Nullable(String)`:
`ClickHouseService` writes an empty string when one is missing rather than a
NULL, because a NULL binding into a non-nullable column fails the entire batch,
not just that row.

---

## 3. Creating an analytics event

Inject `AnalyticsService` and call `track`:

```java
analyticsService.track(
        AnalyticsEventType.BOND_VIEW,   // what happened
        userId,                         // Long, or null for anonymous traffic
        sessionId,                      // String, or null when there is no session
        bondId,                         // Long, or null for events without a bond
        "WEB",                          // the channel
        "BOND_DETAILS",                 // the screen
        Map.of(                         // event-specific extras
                "source", "bond-list",
                "action", "open"
        )
);
```

Every parameter:

| Parameter | Type | Meaning | When null |
|---|---|---|---|
| `eventType` | `AnalyticsEventType` | What happened. Required. | The call is logged and skipped — there is no event. |
| `userId` | `Long` | The signed-in user. | Stored as SQL NULL. Use null for anonymous traffic; never use `0`. |
| `sessionId` | `String` | The browser session. | Stored as an empty string. |
| `bondId` | `Long` | The bond in play. | Stored as SQL NULL. |
| `source` | `String` | The channel: `WEB`, `MOBILE`, … | Stored as an empty string. |
| `page` | `String` | The screen that raised it: `BOND_DETAILS`, `RFQ`, … | Stored as an empty string. |
| `metadata` | `Map<String, Object>` | Extras with no column of their own. | Stored as `{}`. |

`AnalyticsService` generates `eventId` (a random UUID) and `eventTime`
(`Instant.now()`) itself, and copies `metadata` so a caller mutating its map
afterwards cannot change the event.

---

## 4. Calling `AnalyticsService` from a business service

```java
@Service
@RequiredArgsConstructor
public class BondService {

    private final BondRepository bondRepository;
    private final AnalyticsService analyticsService;

    public BondResponse getBond(Long bondId, Long userId, String sessionId) {

        Bond bond = bondRepository.findById(bondId)
                .orElseThrow(() -> new NotFoundException("Bond not found"));

        // After the work that could fail, before returning.
        analyticsService.track(
                AnalyticsEventType.BOND_VIEW,
                userId,
                sessionId,
                bondId,
                "WEB",
                "BOND_DETAILS",
                Map.of()
        );

        return BondResponse.from(bond);
    }
}
```

That is the whole integration. `BondService`:

- knows about `AnalyticsService` and nothing else in this module;
- does not know the topic name, that a topic exists, or that Kafka is involved;
- does not know ClickHouse exists, and writes no analytics SQL;
- performs no insert of its own for analytics;
- does not call the producer, the consumer or the batch buffer.

`AnalyticsService` builds the event. Kafka delivers it asynchronously. The
consumer batches it. `ClickHouseService` stores it. A change to any of those
— a different topic, a different table, a switch to a different sink — is
invisible to `BondService`.

### Where to put the call

**After the operation has succeeded.** An event for an operation that then
failed is worse than no event — it reports a bond view that never rendered.
Place the call after the last step that can throw for a business reason and
before building the response.

**Not inside a database transaction if you can avoid it.** The call itself is
cheap (it does not touch a database), but a rollback after `track` has run
leaves a stored event describing something that was undone.

**Never in a loop.** One event per user action. A list of 50 bonds rendered on
a page is one `SEARCH`, not 50 `BOND_VIEW`s.

**Never on a path where the response depends on it.** `track` returns void and
never throws; do not branch on it.

---

## 5. Event types

`AnalyticsEventType` is closed. Adding a value is a code change, which is the
point: the column is `LowCardinality(String)` and a free-form string would let
a typo quietly create a new category.

| Type | Meaning | Trigger it when |
|---|---|---|
| `LOGIN` | A user signed in. | After authentication succeeds, once per session. |
| `LOGOUT` | A user signed out. | When the session is explicitly ended. |
| `BOND_VIEW` | A bond detail page was opened. | On a successful bond detail fetch. |
| `BOND_CLICK` | A bond was clicked in a list. | On the click, before the detail page loads. |
| `RFQ_VIEW` | The RFQ screen was opened. | When the RFQ form is rendered. |
| `RFQ_CLICK` | An RFQ was opened from a listing. | On the click that leads to an RFQ. |
| `RFQ_SUBMIT` | An RFQ was submitted. | After the RFQ is accepted by the backend. |
| `ORDER_VIEW` | An order screen was opened. | When an order is displayed. |
| `ORDER_SUBMIT` | An order was placed. | After the order is confirmed, not before. |
| `SEARCH` | A search or filter was applied. | On the request that carries the query or filter. |

`VIEW` means "the user was shown this". `CLICK` means "the user chose this".
`SUBMIT` means "the backend accepted this". Keeping the three apart is what
makes a funnel readable.

---

## 6. Metadata conventions

`metadata` holds the detail that does not deserve a column of its own. It is
stored as a JSON object string and can be queried with ClickHouse's
`JSONExtract*` functions.

```java
Map.of(
        "action", "open",
        "source", "bond-list",
        "filter", "AAA",
        "sort", "yield-desc"
)
```

Rules of thumb:

- **Core query fields stay structured.** `userId`, `bondId`, `eventType`,
  `eventTime`, `source` and `page` are columns. Never duplicate them into
  `metadata` — the columns are what the sorting key and the partition use, and
  a value inside a JSON string cannot use either.
- **Keys are `lowerCamelCase`, values are primitives or strings.** Nested
  objects and arrays work but are much harder to query; flatten first.
- **Reuse key names across event types.** `filter`, `sort` and `action` should
  mean the same thing everywhere.
- **Bound the size.** A few keys. This is a `String` column that gets written
  and stored for every event.
- **Never put secrets or personal data in it.** Analytics tables are usually
  read by more people, and kept longer, than the operational database.

---

## 7. Batching behaviour

Behaviours below describe the current implementation, not aspirations.

**Batch size — 1000.** `AnalyticsBatchService.BATCH_SIZE`. When the buffer
reaches it, a flush is triggered by the event that filled it.

**Flush interval — 5 seconds.** `@Scheduled(fixedDelay = 5000)` on
`scheduledFlush`, measured from the end of the previous run. At low traffic this
is what bounds how long an event waits: an idle system still writes its handful
of events within five seconds.

**Order of removal.** Events are removed from the buffer *before* the insert,
not after. A slow insert therefore cannot be overtaken by a second flush that
would send the same events again.

**On a ClickHouse failure.** The affected events are put back at the front of
the buffer in their original order, and the error is logged. The scheduled flush
retries every five seconds. `add` stops starting new flushes for one interval
after a failure, so an outage does not turn every incoming event into another
failed attempt against a service that is already struggling.

**Buffer ceiling — 10 000.** If ClickHouse stays down while traffic continues,
the buffer stops growing and the oldest events are dropped, counted in
`droppedEventCount()` and logged at most once per flush interval. Dropping the
oldest rather than the newest is deliberate: the newest events are what an
operator watching an incident is looking for.

**On shutdown.** `@PreDestroy` flushes whatever is buffered.

### Can events be lost?

**Yes, in three cases.** This is at-least-once from Kafka to the consumer, not
end-to-end durability.

| Case | What is lost |
|---|---|
| The JVM is killed (`kill -9`, OOM, power loss) | Everything buffered but not yet flushed — up to 1000 events, or up to the 10 000 ceiling during an outage. A graceful shutdown flushes first. |
| ClickHouse unavailability longer than the buffer can absorb | The events that overflow the 10 000 ceiling. |
| Kafka being unreachable when `track` is called | The event, logged as an error. Analytics never fails a business request, so this is silent to the user by design. |

### Can events be duplicated?

**Yes, in one case.** Nothing in the path is idempotent. If the application
crashes after `ClickHouseService` has executed the insert but before the Kafka
offset is committed, the consumer redelivers those records on restart and they
are inserted a second time.

Kafka's own offset handling does not duplicate on its own — the container
commits after the listener returns, and the buffer hands each event to exactly
one flush. The window is the crash between insert and commit.

The table has no `ReplacingMergeTree` and no deduplication on `event_id`, so
duplicates are stored as separate rows. Anything counting events should be aware
of this; `SELECT count(DISTINCT event_id)` is the safe form. See the production
section for the fix.

### Kafka offsets

`enable-auto-commit: false` and no `Acknowledgment` parameter on the listener,
so the container manages the offset and commits it in batches after
`AnalyticsEventConsumer.onAnalyticsEvent` returns. The listener only buffers —
it never waits for ClickHouse — so the offset is committed while the event is
still in memory.

That is the trade recorded in the loss table above: offsets advance ahead of
durability. Committing only after a successful ClickHouse write would close the
window but would mean blocking the consumer on a database, which is the
complexity this module deliberately does not take on yet.

---

## 8. Development smoke test

`AnalyticsTestController` exposes one endpoint, bound to the `dev` profile. It
is never registered in any other environment, so the path simply does not exist
in production.

```bash
curl -X POST "http://localhost:8080/api/analytics/test?userId=123&bondId=456"
```

```json
{
  "status": "published",
  "eventType": "BOND_VIEW",
  "source": "TEST",
  "page": "ANALYTICS_TEST",
  "sessionId": "analytics-smoke-test",
  "note": "Accepted by the producer. The row reaches ClickHouse on the next batch flush."
}
```

`userId`, `sessionId` and `bondId` are all optional.

A `202` only means the producer accepted the event. The row appears after the
next flush — up to five seconds later. Test events carry `source = 'TEST'`, so
they can be filtered out of real analytics:

```sql
SELECT * FROM click4bonds_analytics.analytics_events WHERE source != 'TEST';
```

---

## 9. Verifying data

```sql
-- Everything, newest first
SELECT * FROM click4bonds_analytics.analytics_events
ORDER BY event_time DESC LIMIT 20;

-- Just the smoke-test traffic
SELECT event_type, user_id, bond_id, event_time, metadata
FROM click4bonds_analytics.analytics_events
WHERE source = 'TEST'
ORDER BY event_time DESC LIMIT 10;

-- Volume per event type today
SELECT event_type, count()
FROM click4bonds_analytics.analytics_events
WHERE event_time >= today()
GROUP BY event_type ORDER BY count() DESC;

-- Distinct users per bond
SELECT bond_id, uniqExact(user_id)
FROM click4bonds_analytics.analytics_events
WHERE event_type = 'BOND_VIEW' AND bond_id IS NOT NULL
GROUP BY bond_id ORDER BY 2 DESC LIMIT 10;

-- Reading a metadata key (this is why metadata is real JSON)
SELECT JSONExtractString(metadata, 'filter') AS filter, count()
FROM click4bonds_analytics.analytics_events
WHERE event_type = 'SEARCH'
GROUP BY filter ORDER BY 2 DESC;
```

Over HTTP, or inside the container:

```bash
curl "http://localhost:8123/?query=SELECT+count()+FROM+click4bonds_analytics.analytics_events"

docker exec -it click4bonds-clickhouse clickhouse-client \
  --query "SELECT * FROM click4bonds_analytics.analytics_events LIMIT 5 FORMAT Vertical"
```

Inspect the topic:

```bash
docker exec -it click4bonds-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic click4bonds.analytics \
  --from-beginning \
  --property print.key=true
```

Note that the topic has three partitions and no auto-creation
(`KAFKA_AUTO_CREATE_TOPICS_ENABLE: false`); the `NewTopic` bean creates it at
application startup.

---

## 10. Running the tests

Unit tests — no Docker, no Kafka, no ClickHouse. This is the default suite:

```bash
./mvnw test -Dtest='Analytics*Test,ClickHouseServiceTest'
```

Integration tests — need the full local stack (Kafka, ClickHouse, plus the
PostgreSQL and Redis the context always uses). They are named `*IT` so Surefire
skips them by default:

```bash
./mvnw test -Dtest=AnalyticsPipelineIT
```

`AnalyticsPipelineIT` posts to the smoke-test endpoint over real HTTP and then
reads the row back out of ClickHouse over ClickHouse's own HTTP interface,
independently of the code that wrote it. It asserts the fields, that `metadata`
parses as JSON, that `user_id`/`bond_id` reach their `Nullable` columns as
NULL, that `event_time` lands within minutes of the server's own `now()` (a
timezone mismatch between the JDBC driver and the server would fail this), and
that the event is written exactly once. It skips itself if ClickHouse is not
reachable.

---

## 11. Production considerations

The pipeline as it stands is correct for local development and for moderate
production traffic, but it is not durable and it is not monitored. What would
need to change, roughly in order of how much it matters:

**Duplicate events.** The crash-between-insert-and-commit window described in
section 7. Two options: switch the table to
`ReplacingMergeTree` with `ORDER BY (event_type, event_time, event_id)` and
deduplicate on `event_id` at query time, or commit the Kafka offset only after
the ClickHouse write succeeds. The first is a schema change with no code
impact; the second turns the consumer into a synchronous link in the chain.

**Delivery guarantees.** Commit-after-flush would make the pipeline
at-least-once end to end. It requires the consumer to wait for ClickHouse, which
means deciding what happens to the offset when the insert fails — today the
events come back to memory and the offset is already committed.

**Dead-letter topic.** A record that cannot be deserialized is currently logged
and skipped by the container's error handler. With a DLQ it would be parked
somewhere inspectable instead of vanishing.

**Bounded buffers and backpressure.** The in-memory buffer is bounded, but the
bound is enforced by dropping events. A production system would rather apply
backpressure — pause the consumer partitions — than discard data.

**Graceful shutdown.** `@PreDestroy` covers `SIGTERM`. It does not cover
`kill -9`, an OOM kill or a container eviction, and the events in memory go with
it. A shutdown hook alone is not a durability story.

**Consumer concurrency.** One listener thread for three partitions today. Raise
`spring.kafka.listener.concurrency` with the partition count, and remember that
`AnalyticsBatchService` is a single shared buffer, so more concurrency means
more threads contending on one lock rather than more throughput.

**Monitoring and alerting.** Nothing currently reports the health of this
pipeline. Worth exposing as counters: `droppedEventCount()`, flush failures,
buffer depth, Kafka consumer lag, and ClickHouse insert latency. The most
important alert is consumer lag — it is the first thing that moves when the
consumer falls behind or dies, and today it would go unnoticed until someone
looked for a missing event.

**Logging.** Failures are logged at ERROR with the exception and the batch size;
successful flushes at DEBUG. There is no per-event logging, which is right at
volume but means a single lost event leaves no trace.

**Kafka retention and topic settings.** The topic is created with 3 partitions
and 1 replica, which is a single-broker development shape. Production needs
replication, a retention policy matched to how long a replay might be needed,
and a deliberate decision about who may create topics — the `NewTopic` bean
creates it on startup.

**ClickHouse partitioning and ordering.** `PARTITION BY toYYYYMM(event_time)`
gives monthly parts, which is sane up to a few hundred million rows a year. The
sorting key `(event_type, event_time)` suits per-type time-range queries; a
dashboard that mostly filters by user or by bond would want a different key, or
a projection, because that access pattern will not use this index at all.
Consider a TTL to age events out.

**Security and credentials.** The local setup has a passwordless, network-open
ClickHouse. Production needs a real password or TLS certificates, ideally a
dedicated user with `INSERT`-only rights on this one table — the application
never reads from ClickHouse, so it does not need `SELECT`. Kafka should not be
plaintext on a shared network. Credentials must come from the environment;
`application-prod.yaml` reads `CLICKHOUSE_URL`, `CLICKHOUSE_USERNAME`,
`CLICKHOUSE_PASSWORD` and `KAFKA_BOOTSTRAP_SERVERS`.

**Schema management.** The table is created by hand today. It should be a
versioned migration, and the insert statement in `ClickHouseService` should be
reviewed whenever the table changes — the two are only kept in step by the
order of the `?` placeholders.
