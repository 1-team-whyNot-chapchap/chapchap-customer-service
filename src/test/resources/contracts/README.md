# Customer-AI Candidate v1 fixture

- Source repository: sibling `Customer-Ai`
- Source path: `tests/contract/fixtures/customer_ai_candidate_v1.json`
- Source `dev`: `bb27063`
- Fixture introduction commit: `68d50fc`
- Contract status: `CANDIDATE`

This copy lets Customer-Service verify the consumer shape without importing Customer-AI implementation code. Keep runtime calls disabled until both repositories pass the bilateral contract matrix and the verified commit pair is recorded.

## Bilateral activation matrix

- Matrix: `customer-ai-bilateral-matrix-v1.json`
- Verified provider baseline: Customer-AI `bb27063fb4fb4bba7063a366480e40384f6f6062`
- Verified consumer baseline: Customer-Service `3bb04a3deec4793fe4b44176081368310ebffe9e`
- Candidate fixture SHA-256: `ABE26EB47D4D509785FE2E1C9BA5345D2FE279B1AA0C73D0CAB19A4E38B78862`

The matrix records local provider and consumer evidence separately from isolated integration evidence. Runtime activation remains blocked until every matrix row and prerequisite is `PASS`, both runtime sides are explicitly enabled, and the contract is `CONFIRMED`.
