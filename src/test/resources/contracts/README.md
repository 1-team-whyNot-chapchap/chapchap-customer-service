# Customer-AI Candidate v1 fixture

- Source repository: sibling `Customer-Ai`
- Source path: `tests/contract/fixtures/customer_ai_candidate_v1.json`
- Source `dev`: `bb27063`
- Fixture introduction commit: `68d50fc`
- Contract status: `CANDIDATE`

This copy lets Customer-Service verify the consumer shape without importing Customer-AI implementation code. Keep runtime calls disabled until both repositories pass the bilateral contract matrix and the verified commit pair is recorded.

## Bilateral activation matrix

- Runtime manifest: `src/main/resources/contracts/customer-ai-bilateral-matrix-v1.json`
- Verified provider baseline: Customer-AI `6e2d036a46be01030e55c5a0cde261916bfb28c7`
- Verified consumer baseline: Customer-Service `3f53d25acbef498eea24aede8e4ed4cc2b5d2e5f`
- Candidate fixture SHA-256: `ABE26EB47D4D509785FE2E1C9BA5345D2FE279B1AA0C73D0CAB19A4E38B78862`

The matrix records local provider and consumer evidence separately from isolated integration evidence. Runtime activation remains blocked until every matrix row and prerequisite is `PASS`, both runtime sides are explicitly enabled, and the contract is `CONFIRMED`.

`SERVICE_JWT`와 `SUBJECT_ASSERTION`의 격리 PASS는 실제 Java 발급 자료를 임시 HTTPS JWKS로 제공하고 Customer-AI 검증기가 검증한 결과다. 운영 Auth/JWKS 배포 완료를 뜻하지 않으므로 Runtime prerequisite는 별도 PASS 증거가 생길 때까지 BLOCKED다.
