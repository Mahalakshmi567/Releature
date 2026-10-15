# Feature Flag Dashboard

A full-stack feature flag management platform that enables teams to control feature releases, manage environment-specific configurations, target selected users, and gradually roll out new functionality. Built with React, Spring Boot, PostgreSQL, and Redis caching.

## Overview

The Feature Flag Dashboard helps developers release features safely without requiring a new application deployment for every configuration change. Administrators can manage feature flags, control availability across environments, evaluate flags for individual users, and track configuration changes through an audit history.

## Features

### 1. Feature Flag CRUD
- Create, view, edit, enable, disable, and delete feature flags.
- Define a unique flag key, name, description, data type, and default value.
- Support controlled feature evaluation through flag configuration.

### 2. Environment Management
- Manage separate Development, Testing, and Production environments.
- Maintain independent flag configurations for each environment.
- Prevent development configuration changes from automatically affecting production.

### 3. Percentage Rollout
- Gradually release features to a selected percentage of users.
- Use deterministic bucketing so the same user consistently receives the same rollout decision for a given flag configuration.
- Increase or decrease rollout percentages as a release progresses.

### 4. User Targeting
- Target specific user IDs.
- Apply targeting rules based on user attributes, such as subscription plan.
- Configure how explicit targeting interacts with percentage rollout.

### 5. Audit History
- Track feature flag configuration changes.
- Record the previous and updated configuration, change timestamp, responsible user, and reason.
- Maintain a history for reviewing administrative actions.

### 6. Redis Caching and Fast Evaluation
- Cache feature flag configuration to reduce repeated database reads.
- Reuse cached configuration for repeated evaluations.
- Invalidate or refresh cached configuration when an administrator changes a flag.
- Configure cache expiration with a default TTL of 60 seconds.
- Track cache hits, misses, errors, and pending invalidations.
- Support database-backed evaluation when the cache is unavailable, according to the configured fallback behavior.

### 7. One-Click Rollback
- Restore a previous feature flag configuration.
- Preserve the audit trail of configuration changes and rollback actions.
- Roll back configuration only; this does not reverse data changes already performed by the feature.

### 8. Role-Based Permissions
- Protect management endpoints with Spring Security.
- Restrict administrative operations according to user permissions.
- Limit production configuration changes to authorized roles.

## Technology Stack

| Layer | Technology |
|---|---|
| Frontend | React |
| Backend | Java, Spring Boot |
| Database | PostgreSQL |
| Caching | Redis-compatible cache |
| Security | Spring Security, token-based authentication |
| Build tool | Maven |
| Frontend tooling | Node.js, npm |
| Version control | Git, GitHub |

## Architecture

The frontend communicates with the Spring Boot REST API. PostgreSQL stores the persistent application data, while the cache accelerates repeated feature flag evaluations.

```text
React Frontend
      |
      | HTTP / REST API
      v
Spring Boot Backend
      |
      +------ Spring Security
      |
      +------ Feature Flag Evaluation
      |                |
      |                +---- Redis Cache
      |                |
      |                +---- PostgreSQL
      |
      +------ Targeting and Rollout
      |
      +------ Audit History and Rollback
```

## Future Improvements

- Automated integration tests for targeting and rollout behavior.
- Performance testing for high-volume flag evaluation.
- Monitoring and alerting for cache failures.
- CI/CD workflows for automated builds and testing.
- Containerized development and deployment.

