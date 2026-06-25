# ADR 001: Clean Architecture Refactoring

## Status
Accepted

## Context
The me_chess project was identified as having several clean architecture violations during an architecture comparison with similar projects (EJ-Chess-Systems, SeArChess, alu-chess). Key issues included:

1. **Dependency Rule Violations**: The repository layer (`persistent` module) imported and called the service layer (`service.GameService.isGameOver()`), violating the dependency rule that infrastructure should depend on abstractions, not application services.

2. **Layer Mixing in Core Module**: The `core` module mixed domain logic (models, parsers, formatters) with application logic (services, controllers), violating clean architecture's layer separation principle.

3. **Domain Model Contains Application Concerns**: `PositionState` contained application-specific fields like `id`, `creationDate`, time control fields, and game status fields, making it impure from a domain perspective.

4. **Missing Use Case Layer**: The architecture jumped directly from REST API → Controller → Service → Model, missing the use case/interactor layer that should orchestrate business workflows.

5. **Infrastructure Leaks into Repository Layer**: The `persistent` module mixed repository interfaces with concrete implementations (Slick, MongoDB, in-memory), preventing easy swapping of persistence mechanisms.

6. **REST API Directly Depends on Multiple Layers**: The REST API depended on controller, service, repository, and model layers simultaneously, creating a web of dependencies instead of a clean hierarchy.

The comparison with SeArChess (which implements Clean Architecture + DDD) highlighted these issues as preventing the project from achieving the same level of architectural quality and maintainability.

## Decision
We will refactor the project to follow Clean Architecture principles with the following approach:

### 1. Layer Separation
- **Domain Layer (`core`)**: Pure domain logic (models, parsers, formatters, openings, analysis)
- **Application Layer (`application`)**: Services, controllers, use cases (orchestration)
- **Infrastructure Layer**: Concrete implementations (persistence, external APIs, etc.)

### 2. Dependency Rules
- Domain layer has no dependencies on other layers
- Application layer depends only on Domain layer
- Infrastructure layer depends on Domain and Application layer abstractions
- REST API depends only on Application layer and Domain layer

### 3. Module Structure
- Keep `core` for pure domain logic
- Create new `application` module for services, controllers, use cases
- Split `persistent` into interfaces (domain-persistence) and implementations (infrastructure-persistence)
- Update all module dependencies in build.sbt to enforce the dependency rules

### 4. Incremental Migration
- Add domain methods to models to eliminate service dependencies in infrastructure
- Create application module as a stub first, then gradually move code
- Preserve backward compatibility during migration to avoid breaking changes
- Add TODO comments to track future migration steps

## Consequences

### Positive
- **Clearer Boundaries**: Each layer has a well-defined responsibility
- **Better Testability**: Domain logic can be tested without infrastructure
- **Flexibility**: Persistence implementations can be swapped without affecting domain
- **Maintainability**: Changes in one layer don't ripple to others
- **Alignment with Best Practices**: Matches the architecture of reference projects like SeArChess

### Negative
- **Initial Complexity**: More modules and layers to understand
- **Migration Effort**: Requires moving code and updating imports across the codebase
- **Learning Curve**: Team needs to understand clean architecture principles
- **Build Time**: More modules may increase build time slightly

### Neutral
- **Code Volume**: Total lines of code will increase slightly due to additional abstraction layers
- **Module Count**: Project will have more modules (application, domain-persistence, infrastructure-persistence)

## Implementation Plan
The refactoring will be executed incrementally:

1. ✅ **Phase 1 (Completed)**: Remove critical dependency violations
   - Added `isGameOver` domain method to `PositionState`
   - Removed `service.GameService` import from repository layer

2. ✅ **Phase 2 (Completed)**: Create application layer module
   - Added `application` module to build.sbt
   - Created directory structure for future code migration

3. ✅ **Phase 3 (Completed)**: Migrate services and controllers
   - Moved `service/` and `controller/` from `core` to `application`
   - Moved `engine/`, `formatter/`, and `parser/` to `application` (they depend on services)
   - Updated all imports across the codebase
   - Updated module dependencies for tui, gui, rest-api, persistent, lichess-integration-service, tournament-service

4. ✅ **Phase 4 (Completed)**: Add use case layer
   - Created `usecase/` package in application module
   - Created `CreateGameUseCase` for game creation workflows
   - Created `MakeMoveUseCase` for move execution workflows
   - Created `GetGameUseCase` for game query workflows
   - Updated `GameSessionController` to use use cases

5. ✅ **Phase 5 (Completed)**: Split persistent module
   - Created `domain-persistence` module with repository and DAO interfaces
   - Created `infrastructure-persistence` module with concrete implementations
   - Moved all implementations (InMemoryGameRepository, DatabaseGameRepository, DAOs, database configs) to infrastructure-persistence
   - Updated application and rest-api to depend on domain-persistence instead of persistent
   - Marked old `persistent` module as deprecated

6. ✅ **Phase 6 (Completed)**: Enforce boundaries
   - Documented dependency rules in build.sbt for all modules
   - Added explicit DEPENDENCY RULE and FORBIDDEN comments for each module
   - REST API now depends on domain-persistence (interfaces) instead of concrete implementations

## References
- PROJECT_COMPARISON.md - Architecture analysis
- Clean Architecture by Robert C. Martin
- SeArChess project architecture (reference implementation)

## Date
2026-06-21
