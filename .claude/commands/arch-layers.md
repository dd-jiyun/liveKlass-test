# liveKlass Layer Dependency Rules

## Allowed Dependency Direction

```
controller → service, dto
service    → domain, repository, dto
repository → domain
domain     → (none)
dto        → (none)
```

## Violations

### Dependency Direction
- domain package imports from service, repository, controller, or dto
- service package imports from controller
- controller package imports from repository or domain
- repository package imports from service or controller

### Annotation Misuse
- `@Transactional` used in domain, repository, or controller
- `@Setter`, `@Data`, `@Builder`, or `@AllArgsConstructor` used in domain class
- `@ManyToOne` missing fetch attribute or set to `EAGER`
- `@OneToMany` used

### General
- Wildcard imports used
- Business branching logic in controller methods
- Repository declared as class instead of interface
