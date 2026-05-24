# liveKlass Code Quality Rules

## Violations

### Exception Handling
- Empty catch block
- `e.printStackTrace()` used
- Domain exception propagated from service without wrapping in a custom exception
- Business exception caught directly in controller
- Client-facing error message contains internal details (stack trace, SQL, field names)
- Client-facing error message is not written in Korean

### HTTP Status Codes

| Situation | Code |
|-----------|------|
| Input validation failure | 400 |
| Invalid state transition | 400 |
| Duplicate, capacity exceeded | 409 |
| Resource not found | 404 |
| Unexpected server error | 500 |

### Code Quality
- Numeric literal (magic number) used directly in service method
