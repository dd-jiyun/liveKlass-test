# liveKlass REST API Design Rules

## Violations

### URL Naming
- Collection resource URL uses singular noun
- URL path contains a verb
- `@PathVariable` name includes resource type

### Response Format
- Success response not wrapped in `ApiResponse<T>`

### User Identity
- User ID passed via request body or path variable
