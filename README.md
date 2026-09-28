# SpringMind | Evidence-Grounded Enterprise Knowledge Q&A

**English** | [简体中文](README_ZH.md)

SpringMind is a full-stack Retrieval-Augmented Generation (RAG) platform for team knowledge bases. Users can create or join knowledge groups, upload internal documents, and ask questions that produce source-backed, traceable answers—or explicitly decline to answer when the available evidence is insufficient.

The platform provides an end-to-end workflow covering document ingestion, hybrid retrieval, evidence grading, and citation-backed answer generation. The backend follows a `Controller → Service → Mapper` architecture and is organized by business domain, including authentication, knowledge groups, documents, ingestion, retrieval, and Q&A, making the system easy to maintain and extend.

## Key Features

- Group-level data isolation: every retrieval request carries an authenticated user and knowledge-group context; documents, chunks, and search results are isolated by `groupId`.
- Complete document lifecycle: standard and resumable multipart uploads, MinIO object storage, asynchronous ingestion, retry handling, and recovery for stalled jobs.
- Structure-aware parsing: PDF, DOCX, Markdown, and TXT support, with text normalization and structured chunking using overlapping windows.
- Hybrid retrieval: pgvector semantic search and Elasticsearch keyword search run in parallel, with Reciprocal Rank Fusion (RRF) combining their results.
- Query planning: the model chooses whether to search directly, rewrite the query, or decompose it; planning failures fall back to the original question without interrupting the Q&A flow.
- Evidence-constrained answers: retrieved evidence is graded as `NONE / WEAK / PARTIAL / SUFFICIENT`, and the model is restricted to answering from that evidence.
- Traceable citations: answers include source metadata such as document name, document ID, chunk position, and retrieval score.
- Executable quality gates: harness tests enforce core business rules for authentication, group permissions, document lifecycle management, and Q&A retrieval.

## How It Works

```text
Sign up / Sign in
  → Create or join a knowledge group
  → Upload PDF / DOCX / MD / TXT files
  → Store original files in MinIO
  → Parse, clean, and chunk documents asynchronously
  → Build dual indexes with pgvector + Elasticsearch
  → Plan the query and run hybrid retrieval
  → Apply RRF, expand adjacent chunks, and grade the evidence
  → Generate an evidence-constrained answer with an LLM
  → Return the answer, citations, or a reason for declining
```

## Tech Stack

- Backend: Java 21, Spring Boot 3.5, Spring AI 1.1, MyBatis-Plus, Flyway
- Retrieval: PostgreSQL + pgvector, Elasticsearch, Ollama Embeddings
- Storage: MinIO
- Frontend: Vue 3, TypeScript, Pinia, Vue Router, Vite
- Engineering: Docker Compose, JUnit 5, Mockito

The chat model is configured through environment variables and uses an OpenAI-compatible API. Embeddings default to the locally hosted Ollama model `qllama/bge-small-zh-v1.5`.

## Getting Started

### Prerequisites

- Docker Desktop (allocate at least 6 GB of memory)
- An API key for an OpenAI-compatible chat model

### Configure the Model

Create a `.env` file in the project root:

```dotenv
AI_BASE_URL=https://api.openai.com/v1
AI_API_KEY=your-api-key
AI_CHAT_MODEL=gpt-4o-mini
```

You can replace `AI_BASE_URL` and `AI_CHAT_MODEL` with the endpoint and model name of another compatible provider. Never commit a `.env` file containing real credentials.

### Start the Services

```bash
docker compose up -d
```

The first startup downloads the required container images and embedding model, so completion time depends on your network connection. Check the service status and backend logs with:

```bash
docker compose ps
docker compose logs -f backend
```

Service endpoints:

- Web application: <http://localhost:5173>
- Backend health check: <http://localhost:18080/actuator/health>
- API documentation: <http://localhost:18080/doc.html>
- MinIO console: <http://localhost:9001>

Development administrator account:

- Username: `admin`
- Password: `Admin@123456`

The administrator account is used for user management. Regular users can access knowledge groups, documents, and Q&A features after signing up.

### Data and Environment Notes

Flyway manages the database schema, while the Compose project name `springmind` isolates the project's containers and volumes. To reset the local demo data:

```bash
docker compose down -v
```

This command deletes the PostgreSQL, Elasticsearch, MinIO, and Ollama data in the current SpringMind environment. Run it only when you are certain that the data is no longer needed.

## Local Development

Backend:

```bash
mvn test
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

When running the backend locally, you can still start PostgreSQL, Elasticsearch, MinIO, and Ollama separately with Docker:

```bash
docker compose up -d postgres elasticsearch minio ollama ollama-model-init
```

## Project Structure

```text
src/main/java/com/yche/springmind/
├─ ai                 # OpenAI-compatible chat model configuration
├─ auth               # Sign-in, JWT, and refresh tokens
├─ user               # Account security and administrator user management
├─ groupmembership    # Knowledge groups, members, invitations, and join requests
├─ document           # Uploads, resumable multipart uploads, previews, and document status
├─ ingestion          # Parsing, cleaning, chunking, and asynchronous ingestion
├─ retrieval          # pgvector and Elasticsearch adapters
├─ qa                 # Query planning, hybrid retrieval, evidence grading, citations, and answers
└─ storage            # MinIO object storage

frontend/src/
├─ pages              # Sign-in, knowledge groups, documents, Q&A, and admin console
├─ api                # Backend API clients
├─ stores             # Authentication state and the active knowledge group
└─ components         # Shared layout and content components
```

## Key Code Paths

Follow the main document-ingestion and Q&A flows in this order:

1. `DocumentController` → `DocumentService` / `DocumentUploadService`
2. `DocumentIngestionAsyncListener` → `EtlDocumentIngestionProcessor`
3. `StructureAwareChunkTransformer` → `VectorIngestionService` / `ElasticsearchChunkIndexService`
4. `QaController` → `QaService` → `QueryPlanningService`
5. `HybridEvidenceRetriever` → `QaChatService` → `CitationAssembler`

## Testing

Run the core quality gates that do not require a live external model:

```bash
mvn "-Dtest=IdentityAccessHarnessTest,GroupPermissionHarnessTest,DocumentLifecycleHarnessTest,QaRetrievalHarnessTest" test
```

The Flyway integration test requires PostgreSQL to be available locally on port `5433`:

```bash
docker compose up -d postgres
mvn -Dtest=FlywayMigrationTest test
```

See the [Harness documentation](harness/README.md) for the detailed quality-gate rules.

## Design Principles

SpringMind treats the knowledge group as both the data-isolation and authorization boundary, and uses team documents as the sole basis for generated answers. The Q&A pipeline handles only retrievable, verifiable knowledge requests. When evidence is insufficient, it returns an explicit reason for declining to answer, and every citation is produced from actual retrieved content. New capabilities must preserve four engineering constraints: permission isolation, measurable quality, observable cost, and recoverable failures.
