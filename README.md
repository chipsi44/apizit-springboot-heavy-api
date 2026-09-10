# APIZIT Spring Boot Heavy reference API

Run a predictable API locally and compare its behavior with the Flask,
FastAPI and Linking reference fixtures. This repository is standalone and MIT licensed.
The public Light repository and its private mirror contain identical files.

## Run locally

```sh
# Java 21 and Maven 3.9+
mvn -B verify
java -jar target/reference-api.jar
```

The default URL is `http://127.0.0.1:8000`. No database, account or cloud credentials
are needed. This is a development fixture, not a production service.

## HTTP contract

| Method | Path |
| --- | --- |
| `GET` | `/health` |
| `GET` | `/info` |
| `POST` | `/echo` |
| `GET` | `/items/{item_id}` |
| `GET` | `/slow` |
| `GET` | `/ready` |
| `POST` | `/text/embedding` |
| `POST` | `/text/similarity` |
| `POST` | `/image/analyze` |
| `POST` | `/image/embedding` |

- `GET /health` returns exactly `{"status":"ok"}` without loading models.
- `GET /info` identifies `framework: "springboot"` and `profile: "heavy"`.
- `POST /echo` accepts `{"message":"hello","count":2}` and returns
  `{"received":{"message":"hello","count":2}}`. Count must be an integer,
  never a boolean; message must be a nonempty string.
- `GET /items/7?include_details=true` returns
  `{"item_id":7,"include_details":true,"details":"Reference item 7"}`.
  Details are omitted by default. IDs are positive integers; the query accepts true/false.
- `GET /slow` waits exactly **80 seconds**, then returns
  `{"delay_seconds":80,"status":"completed"}`. It is a duration probe, never a
  health check. Routine tests replace the sleep function; use `curl --max-time 90
  http://127.0.0.1:8000/slow` only for an intentional real-duration smoke.
- Invalid input receives a framework-native 4xx response. No caller-supplied URL,
  filesystem path or code is executed. All data is disposable.

```sh
curl http://127.0.0.1:8000/health
curl -H 'Content-Type: application/json' -d '{"message":"hello","count":2}' http://127.0.0.1:8000/echo
curl 'http://127.0.0.1:8000/items/7?include_details=true'
```

## Heavy workload

This Java fixture uses real OpenCV native image processing and Apache Commons Math
linear algebra. The native platform dependency deliberately includes the vendor's
supported CPU binaries; no padding files or unused ML libraries inflate the package.
`/ready` lazily initializes the CPU feature pipelines and returns 200 or 503.
`/text/embedding` accepts `{"text":"hello"}`; `/text/similarity` accepts
`{"left":"hello","right":"world"}`. Both use deterministic hashed character
features projected through a seeded 256x256 singular-value decomposition.
`/image/analyze` and `/image/embedding` accept multipart `file` (PNG/JPEG, 4 MiB,
20 million pixels). OpenCV resizes images and derives normalized color features.
Analyze returns width/height/format and color-channel scores; embedding returns a
48-dimensional color histogram. Inputs are bounded, and one feature request runs
at a time (429 for overlap). All native buffers are closed after use.

The five additional HTTP paths and response shapes match the Python Heavy fixtures.
The Java algorithms are **deterministic feature probes**, not pretrained semantic
embeddings or object recognition. The `model` field names the actual algorithm.
No model weights or external service are required. CI runs real CPU computation
and native image processing, keeping `/health` independent of lazy initialization.


## APIZIT qualification status

The matching platform adapter and hosted launch path are being qualified in the
APIZIT development environment. Local tests and a published fixture do not prove
a hosted launch. Follow the platform's reference qualification evidence for the
exact source commits and observed results. This fixture needs no APIZIT checkout.

The measured scan and current backend commercial catalog determine launch eligibility.
No deployment profile, paid plan or production readiness is promised by this repository.

See CONTRIBUTING.md for repeatable checks.
