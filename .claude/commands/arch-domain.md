# liveKlass Domain Invariant Rules

## Core Principle

Domain objects are solely responsible for their own invariants. Business rules belong inside domain methods, not in the service layer.

## Violations

### Object Creation
- Constructor called directly outside the domain class
- Service validates creation conditions before calling `create()`

### State Transitions
- Service checks current state directly before calling a domain method to guard it
- State field mutated directly without going through a domain method

### Condition Encapsulation
- Service or controller uses raw field comparisons as business conditions

### Naming
- Domain boolean method does not use `is` / `has` prefix
- State transition method is not in verb form
- Public method named with vague terms such as `process`, `handle`, `manage`, or `do`
