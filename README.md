# Releature - Feature Flag Dashboard

A full-stack feature flag management system that enables developers to control feature releases, manage environment-specific configurations, target users, and roll out new features safely without redeploying their applications.

The platform provides centralized flag management, deterministic percentage-based rollouts, audit history, role-based access control, Redis caching, configuration rollback, and real-time change notifications.

✨ Features

🔹 Level 1 — Core Features

1. Feature Flag CRUD

Create, view, edit, enable, disable, and delete feature flags.

Assign each flag a unique key, name, description, data type, and default value.

Validate flag configurations to maintain data consistency.

2. Environment Management

Maintain independent configurations for Development, Testing, and Production.

Modify development settings without automatically affecting production.

Manage feature availability separately for each environment.

3. Percentage-Based Rollout

Release features gradually to a specified percentage of users.

Use deterministic bucketing to assign users consistently.

Ensure the same user receives a stable evaluation result for a given flag and configuration.

4. User Targeting

Enable features for explicitly selected user IDs.

Target users based on attributes such as subscription plan.

Define precedence between explicit targeting rules and percentage-based rollout.

Support controlled feature releases for specific user segments.

5. Audit History

Track feature flag configuration changes.

Record the user who made each change.

Store previous and updated configurations.

Record timestamps and reasons for changes.

Maintain a history of administrative actions for traceability.

🔹 Level 2 — Advanced Features

6. Redis Caching and Fast Evaluation

Cache feature flag configurations using Redis.

Reduce repeated database queries during flag evaluation.

Refresh or invalidate cached configurations after administrative updates.

Improve evaluation performance and application responsiveness.

7. One-Click Configuration Rollback

Restore a previous feature flag configuration.

Preserve audit history for rollback operations.

Record the restored configuration and the user initiating the rollback.

Roll back configuration changes without reverting application data or database changes caused by the feature.

8. Role-Based Access Control

Secure management endpoints using Spring Security.

Allow authorized administrators to create, update, enable, disable, and delete flags.

Restrict production configuration changes to users with appropriate permissions.

Enforce authorization on the backend rather than relying only on dashboard controls.

9. Real-Time Change Notifications

Notify connected dashboards when a feature flag configuration changes.

Use WebSockets or Server-Sent Events (SSE) to deliver change notifications.

Refresh affected flag configurations without restarting the application.

Keep the dashboard synchronized with administrative changes.

Coordinate cache invalidation with configuration updates to prevent stale evaluations.

🛠️ Technology Stack

Technology

Purpose

React

Frontend dashboard

Java

Backend application logic

Spring Boot

REST APIs and backend services

Spring Security

Authentication and authorization

PostgreSQL

Persistent storage for flags, configurations, and audit records

Redis

Configuration caching

WebSockets / SSE

Real-time change notifications

Maven

Backend dependency management

Git and GitHub

Version control and collaboration
