# liveKlass Test Rules

## Violations

### Structure
- Unit test class imports Spring context (`@SpringBootTest`, `@ExtendWith(SpringExtension.class)`)
- Integration test class annotated with `@Transactional`

### Naming
- Test method name does not start with `should`
- `@DisplayName` missing or does not describe the expected outcome as a full sentence

### Code Quality
- Divider comments used (e.g., `// ───`, `// ---`)
