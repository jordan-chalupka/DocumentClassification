# Document Classification Demo

This is a Spring Boot application that classifies insurance-related documents using the OpenAI API. The application is designed to handle PDF files, upload them to a vector store, and classify them based on predefined categories.

## Dependencies

- Java 22 
- Gradle
- OpenAI API Key
- Docker (for deployment)

## Setup

1. **Set an OpenAI API Key Environment Variable**:
```bash
export OPENAI_API_KEY=sk-123
```

2. **Build**
```bash
./gradlew build
```

3. **Run**
```bash
./gradlew bootRun
```

### Usage:
To classify a document, upload a PDF file via the /api/completion endpoint:

```bash
curl -X GET http://localhost:8080/api/completion -F "file=@path-to-your-file.pdf"
```

example:
```bash
 curl -X GET http://localhost:8080/api/completion -F "file=@src/main/resources/sample-insurance-documents/Sample_Insurance_Cert.pdf"
```

Alternatively use the python script to run through all the given examples:
```bash
python scripts/run_all_docs.python
```


Optional:
- Deploy using Fly.io (simple dockerized deployment service)
- See fly.toml for details.
```bash
fly deploy
```
