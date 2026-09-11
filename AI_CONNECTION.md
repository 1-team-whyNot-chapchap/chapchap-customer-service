# Customer / Auth / AI connection readiness

Use ISOLATED mode and the ai-isolated Spring profile for verification. Do not replace the CANDIDATE/BLOCKED bilateral manifest with fabricated PASS evidence. ACTIVE remains gated.

## Identity and callback wiring

- Auth owns service JWT private/public keys and distinct customer-service/customer-ai client secrets.
- Customer owns a separate Subject RSA key pair. Its public JWKS is `/.well-known/customer-ai-subject-jwks.json`, exposed only while internal-auth is enabled.
- AI trusts Auth `/.well-known/jwks.json` and Customer Subject JWKS over HTTPS. Match issuer, audience, kid and short lifetimes from existing contracts.
- Customer obtains `customer-ai.invoke` service tokens; AI obtains `customer-ai.callback` service tokens for authenticated knowledge/summary callbacks.
- Callback addresses target Customer directly, never the public Gateway; do not forward browser JWTs or Gateway headers to AI.

Customer settings: `config/ai-isolated.env.example`. AI settings: sibling Customer-Ai `config/provider-runtime.env.example`. The two files describe opt-in settings, not runnable completed environments.

Run the existing cross-language check from this repository:

```powershell
.\scripts\run-internal-auth-isolated-contract.ps1
```

It uses temporary generated test keys and localhost TLS to test real Java issuers against the Python JWKS verifier, including rotation and failure handling. It does not prove that persistent Auth/Customer/AI servers or business callbacks are reachable.

## Readiness sequence

1. Configure trusted HTTPS Auth, Customer and AI origins and CA trust in Java/Python; never disable TLS verification.
2. Align actual client secrets and Subject key material (private key stays Customer-only).
3. Provide AI DeepSeek key, allowed MinIO hosts and RAG dependencies; provider runtime currently requires these even before consultation verification.
4. Start opt-in isolated servers. Verify wrong JWT/scope/subject/callback rejection and successful knowledge/summary callback state transitions.
5. Then validate Delivery's matching dedicated key and URL, Subscription live reads, MinIO source download, E5/Chroma and knowledge-to-answer flow.

Until these prerequisites pass, preserve disabled defaults. Database migrations, real customer writes and production activation require separately scoped verification.
